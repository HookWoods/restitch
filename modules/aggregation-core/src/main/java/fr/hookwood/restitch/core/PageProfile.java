package fr.hookwood.restitch.core;

import java.util.Map;

public record PageProfile(String itemsPointer, Map<String, String> metadataPointers) {
    public PageProfile {
        JsonPointers.requirePointer(itemsPointer, "itemsPointer");
        metadataPointers = Map.copyOf(metadataPointers == null ? Map.of() : metadataPointers);
        metadataPointers.forEach((name, pointer) -> JsonPointers.requirePointer(pointer, "metadata pointer " + name));
    }
}
