package com.opinio.passform.domain.form;

import java.io.Serializable;
import java.util.UUID;

public record FormVersionId(UUID formId, int version) implements Serializable {
}
