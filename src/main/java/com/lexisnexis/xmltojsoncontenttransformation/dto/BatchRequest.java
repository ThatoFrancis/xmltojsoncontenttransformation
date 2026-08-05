package com.lexisnexis.xmltojsoncontenttransformation.dto;

import jakarta.validation.constraints.NotBlank;

public record BatchRequest(@NotBlank String inputDir) {
}
