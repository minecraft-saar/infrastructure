package de.saar.minecraft.architect;

/**
 * A factory for creating Architect instances of a certain type.
 *
 */
@FunctionalInterface
public interface ArchitectFactory {
    public Architect build();
}
