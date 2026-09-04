package com.lovemaptually.dto.request;
import jakarta.validation.constraints.*;
public record CreateGroupRequest(@NotBlank String groupType,@Size(max=50) String name) {}
