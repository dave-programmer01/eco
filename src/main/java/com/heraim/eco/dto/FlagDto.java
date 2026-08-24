package com.heraim.eco.dto;

import com.heraim.eco.model.Level;

public record FlagDto(
    Level level,
    String reason,
    String quotedSpan
){

}
