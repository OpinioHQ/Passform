package com.opinio.passform.domain.inbox;

import java.io.Serializable;
import java.util.UUID;

public record SavedFormId(UUID userId, UUID formId) implements Serializable {
}
