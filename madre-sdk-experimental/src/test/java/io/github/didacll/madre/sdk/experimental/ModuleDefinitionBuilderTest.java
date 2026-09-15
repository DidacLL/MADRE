package io.github.didacll.madre.sdk.experimental;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.material.MaterialCodec;
import io.github.didacll.madre.sdk.material.MaterialType;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class ModuleDefinitionBuilderTest {
    @Test
    void buildsExistingStableDomainObject() {
        ModuleId moduleId = new ModuleId("example.experimental");
        MaterialType<String> request = textType(moduleId, "request");

        var definition = ModuleDefinitionBuilder.module(moduleId, "0.1.0", "example")
                .materialType(request)
                .build();

        assertEquals(moduleId, definition.id());
        assertEquals(request, definition.materialTypes().get(request.id()));
    }

    @Test
    void rejectsDuplicateTypedDeclarationsDuringAuthoring() {
        ModuleId moduleId = new ModuleId("example.experimental");
        MaterialType<String> request = textType(moduleId, "request");
        ModuleDefinitionBuilder builder = ModuleDefinitionBuilder.module(
                moduleId, "0.1.0", "example").materialType(request);

        assertThrows(IllegalArgumentException.class, () -> builder.materialType(request));
    }

    private static MaterialType<String> textType(ModuleId moduleId, String name) {
        return new MaterialType<>(new MaterialTypeId(moduleId, name), String.class,
                "text/plain; charset=utf-8", new MaterialCodec<>() {
                    @Override public byte[] encode(String value) {
                        return value.getBytes(StandardCharsets.UTF_8);
                    }
                    @Override public String decode(byte[] bytes) {
                        return new String(bytes, StandardCharsets.UTF_8);
                    }
                });
    }
}
