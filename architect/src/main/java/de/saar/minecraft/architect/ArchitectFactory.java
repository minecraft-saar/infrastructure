package de.saar.minecraft.architect;

/**
 * A factory for creating Architect instances of a certain type.
 *
 */
@FunctionalInterface
public interface ArchitectFactory {
    /** creates architects.
     * @return returns the new architect**/
    Architect build();
}
