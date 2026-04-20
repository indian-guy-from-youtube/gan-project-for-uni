"""
Style Transfer GAN Server
Flask REST API для переноса художественного стиля с использованием
предобученных весов CycleGAN (monet, vangogh, ukiyoe, cezanne)
и Neural Style Transfer через torchvision.
"""

import os
import io
import uuid
import logging
import time
from pathlib import Path

import torch
import torch.nn as nn
from torchvision import transforms
from torchvision.models import vgg19
from PIL import Image
import numpy as np
from flask import Flask, request, jsonify, send_file
from flask_cors import CORS

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)  # разрешаем запросы от Spring backend

DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")
logger.info(f"Using device: {DEVICE}")

# Папки
WEIGHTS_DIR = Path("weights")
OUTPUT_DIR = Path("outputs")
OUTPUT_DIR.mkdir(exist_ok=True)
WEIGHTS_DIR.mkdir(exist_ok=True)

# ─────────────────────────────────────────────
# CycleGAN Generator (ResNet-based, 9 блоков)
# Совместим с официальными весами junyanz/pytorch-CycleGAN-and-pix2pix
# ─────────────────────────────────────────────

class ResnetBlock(nn.Module):
    def __init__(self, dim):
        super().__init__()
        self.block = nn.Sequential(
            nn.ReflectionPad2d(1),
            nn.Conv2d(dim, dim, 3),
            nn.InstanceNorm2d(dim),
            nn.ReLU(True),
            nn.ReflectionPad2d(1),
            nn.Conv2d(dim, dim, 3),
            nn.InstanceNorm2d(dim),
        )

    def forward(self, x):
        return x + self.block(x)


class CycleGANGenerator(nn.Module):
    """
    Генератор CycleGAN: encoder → 9 ResNet блоков → decoder
    Архитектура идентична официальной реализации junyanz.
    """
    def __init__(self, input_nc=3, output_nc=3, ngf=64, n_blocks=9):
        super().__init__()
        layers = [
            nn.ReflectionPad2d(3),
            nn.Conv2d(input_nc, ngf, 7),
            nn.InstanceNorm2d(ngf),
            nn.ReLU(True),
            # Downsampling
            nn.Conv2d(ngf, ngf*2, 3, stride=2, padding=1),
            nn.InstanceNorm2d(ngf*2),
            nn.ReLU(True),
            nn.Conv2d(ngf*2, ngf*4, 3, stride=2, padding=1),
            nn.InstanceNorm2d(ngf*4),
            nn.ReLU(True),
        ]
        for _ in range(n_blocks):
            layers.append(ResnetBlock(ngf*4))
        layers += [
            # Upsampling
            nn.ConvTranspose2d(ngf*4, ngf*2, 3, stride=2, padding=1, output_padding=1),
            nn.InstanceNorm2d(ngf*2),
            nn.ReLU(True),
            nn.ConvTranspose2d(ngf*2, ngf, 3, stride=2, padding=1, output_padding=1),
            nn.InstanceNorm2d(ngf),
            nn.ReLU(True),
            nn.ReflectionPad2d(3),
            nn.Conv2d(ngf, output_nc, 7),
            nn.Tanh(),
        ]
        self.model = nn.Sequential(*layers)

    def forward(self, x):
        return self.model(x)


# ─────────────────────────────────────────────
# Менеджер моделей — ленивая загрузка весов
# ─────────────────────────────────────────────

class StyleModelManager:
    """
    Управляет загрузкой и кешированием моделей.
    Поддерживаемые стили:
      - monet    → живопись Моне
      - vangogh  → Ван Гог
      - ukiyoe   → японская гравюра укиё-э
      - cezanne  → Сезанн
    """

    # Официальные веса с Google Drive (репо junyanz/pytorch-CycleGAN-and-pix2pix)
    WEIGHT_URLS = {
        "monet":   "https://people.eecs.berkeley.edu/~taesung_park/CycleGAN/models/monet_pretrained.pth",
        "vangogh": "https://people.eecs.berkeley.edu/~taesung_park/CycleGAN/models/vangogh_pretrained.pth",
        "ukiyoe":  "https://people.eecs.berkeley.edu/~taesung_park/CycleGAN/models/ukiyoe_pretrained.pth",
        "cezanne": "https://people.eecs.berkeley.edu/~taesung_park/CycleGAN/models/cezanne_pretrained.pth",
    }

    def __init__(self):
        self._cache: dict[str, CycleGANGenerator] = {}

    def get_model(self, style: str) -> CycleGANGenerator:
        if style not in self.WEIGHT_URLS:
            raise ValueError(f"Неизвестный стиль: {style}. Доступны: {list(self.WEIGHT_URLS.keys())}")

        if style not in self._cache:
            self._cache[style] = self._load(style)

        return self._cache[style]

    def _load(self, style: str) -> CycleGANGenerator:
        weight_path = WEIGHTS_DIR / f"{style}.pth"
    
        if not weight_path.exists():
            logger.info(f"Скачиваю веса для стиля '{style}'...")
            self._download(self.WEIGHT_URLS[style], weight_path)
    
        logger.info(f"Загружаю модель '{style}' с {weight_path}")
        model = CycleGANGenerator()
    
        state = torch.load(weight_path, map_location=DEVICE)
    
        if isinstance(state, dict):
            if "G_A" in state:
                state = state["G_A"]
            elif "model" in state:
                state = state["model"]
    
        clean = {}
        for k, v in state.items():
            # убираем префикс module. (DataParallel)
            k = k.replace("module.", "")
            # переименовываем conv_block → block (разница в названии)
            k = k.replace(".conv_block.", ".block.")
            # убираем устаревшие ключи InstanceNorm
            if k.endswith(".running_mean") or k.endswith(".running_var") or k.endswith(".num_batches_tracked"):
                continue
            clean[k] = v
    
        model.load_state_dict(clean, strict=True)
        model.to(DEVICE)
        model.eval()
        logger.info(f"Модель '{style}' загружена успешно")
        return model

    @staticmethod
    def _download(url: str, dest: Path):
        import urllib.request
        urllib.request.urlretrieve(url, dest)


model_manager = StyleModelManager()

# ─────────────────────────────────────────────
# Трансформации изображений
# ─────────────────────────────────────────────

def preprocess(img: Image.Image, size: int = 512) -> torch.Tensor:
    t = transforms.Compose([
        transforms.Resize(size, Image.BICUBIC),
        transforms.CenterCrop(size),
        transforms.ToTensor(),
        transforms.Normalize((0.5,)*3, (0.5,)*3),
    ])
    return t(img).unsqueeze(0).to(DEVICE)


def postprocess(tensor: torch.Tensor) -> Image.Image:
    img = tensor.squeeze(0).detach().cpu()
    img = img * 0.5 + 0.5          # [-1,1] → [0,1]
    img = img.clamp(0, 1)
    img = transforms.ToPILImage()(img)
    return img


# ─────────────────────────────────────────────
# REST API endpoints
# ─────────────────────────────────────────────

@app.route("/health", methods=["GET"])
def health():
    """Проверка работоспособности сервера."""
    return jsonify({
        "status": "ok",
        "device": str(DEVICE),
        "available_styles": list(StyleModelManager.WEIGHT_URLS.keys()),
        "cuda_available": torch.cuda.is_available(),
    })


@app.route("/styles", methods=["GET"])
def get_styles():
    """Список поддерживаемых стилей с описанием."""
    styles = {
        "monet":   {"name": "Клод Моне",       "description": "Импрессионизм, мягкие мазки, пастельные тона"},
        "vangogh": {"name": "Винсент Ван Гог",  "description": "Экспрессионизм, вихревые линии, насыщенные цвета"},
        "ukiyoe":  {"name": "Укиё-э",           "description": "Японская гравюра, чёткие контуры, плоские цвета"},
        "cezanne": {"name": "Поль Сезанн",      "description": "Постимпрессионизм, геометричность, плотные мазки"},
    }
    return jsonify({"styles": styles})


@app.route("/transfer", methods=["POST"])
def transfer_style():
    """
    Перенос стиля на изображение.

    Тело запроса (multipart/form-data):
      - image  : файл изображения (JPEG/PNG)
      - style  : название стиля (monet | vangogh | ukiyoe | cezanne)
      - size   : (опционально) размер выходного изображения, по умолчанию 512

    Ответ: JSON с base64-кодированным результатом или поле image_url
    """
    if "image" not in request.files:
        return jsonify({"error": "Поле 'image' обязательно"}), 400

    style = request.form.get("style", "monet").lower()
    size  = int(request.form.get("size", 512))
    size  = min(max(size, 128), 1024)  # ограничиваем 128-1024

    try:
        # Читаем входное изображение
        file = request.files["image"]
        img = Image.open(file.stream).convert("RGB")
        original_size = img.size

        # Получаем модель
        model = model_manager.get_model(style)

        # Инференс
        start = time.time()
        with torch.no_grad():
            tensor_in  = preprocess(img, size)
            tensor_out = model(tensor_in)
        elapsed = time.time() - start

        # Постобработка
        result_img = postprocess(tensor_out)

        # Сохраняем и возвращаем файл
        result_id   = str(uuid.uuid4())
        result_path = OUTPUT_DIR / f"{result_id}.png"
        result_img.save(result_path, "PNG")

        logger.info(f"Style transfer '{style}' completed in {elapsed:.2f}s → {result_path}")

        # Возвращаем изображение напрямую как бинарные данные
        buf = io.BytesIO()
        result_img.save(buf, format="PNG")
        buf.seek(0)

        response = send_file(
            buf,
            mimetype="image/png",
            as_attachment=False,
            download_name=f"styled_{style}_{result_id[:8]}.png",
        )
        response.headers["X-Processing-Time"] = f"{elapsed:.3f}s"
        response.headers["X-Result-Id"] = result_id
        response.headers["X-Original-Size"] = f"{original_size[0]}x{original_size[1]}"
        return response

    except ValueError as e:
        return jsonify({"error": str(e)}), 400
    except Exception as e:
        logger.exception("Ошибка при обработке изображения")
        return jsonify({"error": f"Внутренняя ошибка: {str(e)}"}), 500


@app.route("/transfer/base64", methods=["POST"])
def transfer_style_base64():
    """
    Альтернативный endpoint: принимает и возвращает base64.
    Удобно для Spring backend без сохранения файлов.

    JSON тело:
      {
        "image_base64": "<base64 строка>",
        "style": "vangogh",
        "size": 512
      }
    """
    import base64

    data = request.get_json()
    if not data or "image_base64" not in data:
        return jsonify({"error": "Поле 'image_base64' обязательно"}), 400

    style = data.get("style", "monet").lower()
    size  = int(data.get("size", 512))
    size  = min(max(size, 128), 1024)

    try:
        img_bytes = base64.b64decode(data["image_base64"])
        img = Image.open(io.BytesIO(img_bytes)).convert("RGB")

        model = model_manager.get_model(style)

        start = time.time()
        with torch.no_grad():
            tensor_in  = preprocess(img, size)
            tensor_out = model(tensor_in)
        elapsed = time.time() - start

        result_img = postprocess(tensor_out)

        buf = io.BytesIO()
        result_img.save(buf, format="PNG")
        result_b64 = base64.b64encode(buf.getvalue()).decode("utf-8")

        return jsonify({
            "success": True,
            "style": style,
            "image_base64": result_b64,
            "processing_time_s": round(elapsed, 3),
            "output_size": f"{result_img.width}x{result_img.height}",
        })

    except ValueError as e:
        return jsonify({"error": str(e)}), 400
    except Exception as e:
        logger.exception("Ошибка при обработке base64")
        return jsonify({"error": f"Внутренняя ошибка: {str(e)}"}), 500


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5000))
    debug = os.environ.get("DEBUG", "false").lower() == "true"
    logger.info(f"Запуск сервера на порту {port}, debug={debug}")
    app.run(host="0.0.0.0", port=port, debug=debug)
