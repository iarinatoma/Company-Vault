package com.example.template.folder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateFolderRequest(@NotBlank @Size(max = 100) String name) {}