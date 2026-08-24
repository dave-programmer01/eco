package com.heraim.eco.dto;

import java.util.List;

public record AnalysisResult (
    List<FlagDto> flags
){
}
