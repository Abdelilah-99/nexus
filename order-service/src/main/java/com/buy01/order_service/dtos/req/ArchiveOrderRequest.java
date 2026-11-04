package com.buy01.order_service.dtos.req;

import jakarta.validation.constraints.NotNull;

public record ArchiveOrderRequest(@NotNull(message = "archived is required") Boolean archived) { }
