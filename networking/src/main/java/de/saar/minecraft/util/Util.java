package de.saar.minecraft.util;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * utility functions.
 */
public class Util {
    /**
     * Reads the entire Reader into a string and returns it.
     * @param reader the reader
     * @return the string in the reader
     */
    public static String slurp(Reader reader) {
        try {
            char[] arr = new char[8 * 1024];
            StringBuilder buffer = new StringBuilder();
            int numCharsRead;
            while ((numCharsRead = reader.read(arr, 0, arr.length)) != -1) {
                buffer.append(arr, 0, numCharsRead);
            }
            reader.close();

            return buffer.toString();
        } catch (IOException e) {
            return null;
        }
    }
}
