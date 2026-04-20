package com.ganbackend.gan.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
 
import java.util.Map;
 
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StylesResponse {
 
    private Map<String, StyleInfo> styles;
 
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StyleInfo {
        private String name;
        private String description;
    }
 
    /** Дефолтный список на случай если Python недоступен */
    public static StylesResponse defaults() {
        return new StylesResponse(Map.of(
                "monet",   new StyleInfo("Клод Моне",       "Импрессионизм, мягкие мазки"),
                "vangogh", new StyleInfo("Винсент Ван Гог",  "Экспрессионизм, вихревые линии"),
                "ukiyoe",  new StyleInfo("Укиё-э",           "Японская гравюра"),
                "cezanne", new StyleInfo("Поль Сезанн",      "Постимпрессионизм")
        ));
    }
}