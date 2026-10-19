package org.chenile.jgen.blueprints;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BlueprintMarkerTest {
    @Test void parsesNamesAndStringAttributesIncludingQuotedAndEscapedValues() {
        assertEquals(new BlueprintMarker("minimonolith", Map.of()), BlueprintMarker.parse("%{{minimonolith}}"));
        var marker = BlueprintMarker.parse("%{{chenile-service name=orders role='server worker' label=\"say \\\"hello\\\"\" empty='' enabled=true}}");
        assertEquals("chenile-service", marker.blueprintName());
        assertEquals(Map.of("name", "orders", "role", "server worker", "label", "say \"hello\"", "empty", "", "enabled", "true"), marker.attributes());
    }

    @Test void rejectsMalformedSyntaxAndDuplicateAttributes() {
        for (String input : new String[]{"%{{}}", "%{{child", "%{{child}}extra", "%{{child flag}}", "%{{child key=}}",
                "%{{child key='unterminated}}", "%{{child key=\"value\"extra}}", "%{{child key=x key=y}}",
                "%{{child =x}}", "%{{child key=a=b}}", "%{{child key='bad\\q'}}"}) {
            assertThrows(IllegalArgumentException.class, () -> BlueprintMarker.parse(input), input);
        }
    }
}
