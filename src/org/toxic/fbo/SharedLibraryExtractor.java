package org.toxic.fbo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;

/**
 * <p>
 * Extracts shared libraries from a natives jar file. Adapted from libGDX project code.
 * </p>
 * <br/>
 * <b>Imported from playn.java.SharedLibraryExtractor source</b>
 */
public class SharedLibraryExtractor {

    //constants
    private static final String WORK_DIR = "/work";
    private static final String USER_NAME = "user.name";
    private static final String JAVA_IO_TMPDIR = "java.io.tmpdir";
    private static final String MAC_NATIVE_DYLIB = ".dylib";
    private static final String MAC_NATIVE_JNILIB = ".jnilib";
    private static final String LINUX_NATIVE = ".so";
    private static final String WINDOWS_NATIVE = ".dll";
    private static final String OS_SEPARATOR = "/";
    private static final String LIB = "lib";
    private static final String JAVA_LIBRARY_PATH = "java.library.path";
    private final boolean isWindows = System.getProperty("os.name").contains("Windows");
    private final boolean isLinux = System.getProperty("os.name").contains("Linux");
    private final boolean isMac = System.getProperty("os.name").contains("Mac");
    private final boolean is64Bit = System.getProperty("os.arch").equals("amd64");

    /** Returns a CRC of the remaining bytes in the stream. */
    private String crc(final InputStream input) {
        if (input == null) {
            throw new IllegalArgumentException("input cannot be null.");
        }
        CRC32 crc = new CRC32();
        byte[] buffer = new byte[4096];
        try {
            while (true) {
                int length = input.read(buffer);
                if (length == -1) {
                    break;
                }
                crc.update(buffer, 0, length);
            }
        }
        catch (Exception ex) {
            try {
                input.close();
            }
            catch (Exception ignored) {
                System.out.println("Occurred an exception during cloasing input stream!");
                ignored.printStackTrace();
            }
        }
        return Long.toString(crc.getValue());
    }

    /** Maps a platform independent library name to one or more platform dependent names. */
    private String[] platformNames(final String libraryName) {
        if (isWindows) {
            return new String[] { libraryName + (is64Bit ? "64.dll" : WINDOWS_NATIVE) };
        }
        if (isLinux) {
            return new String[] { LIB + libraryName + (is64Bit ? "64.so" : LINUX_NATIVE) };
        }
        if (isMac) {
            return new String[] { LIB + libraryName + MAC_NATIVE_JNILIB, LIB + libraryName + MAC_NATIVE_DYLIB };
        }
        return new String[] { libraryName };
    }

    private InputStream readFile(final String path) throws IOException {
        InputStream input = getClass().getResourceAsStream(OS_SEPARATOR + path);
        if (input == null) {
            throw new FileNotFoundException("Unable to read file for extraction: " + path);
        }
        System.out.println("Extracted source by path = " + OS_SEPARATOR + path);
        return input;
    }

    /**
     * Extracts the specified library into the specified temp directory (if it has not already been
     * extracted thereto, or if the CRC does not match).
     * @param libraryName 
     *
     * @param sourcePath The file to extract from the classpath or JAR.
     * @param dirName    The name of the subdirectory where the file will be extracted. If null, the
     *                   file's CRC will be used.
     * @return The extracted file.
     * @throws IOException 
     */
    public File extractLibrary(final String libraryName, String dirName) throws IOException {
        File javaLibPath = new File(System.getProperty(JAVA_LIBRARY_PATH));
        System.out.println("Java library path = " + javaLibPath.getAbsolutePath());
        for (String sourcePath : platformNames(libraryName)) {
            System.out.println("Find source by path = " + sourcePath);
            InputStream cinput;
            try {
                cinput = readFile(sourcePath);
            }
            catch (FileNotFoundException fnfe) {
                // attempt to fallback to file at java.library.path location
                System.out.println("attempt to fallback to file at java.library.path location!");
                fnfe.printStackTrace();
                File file = new File(javaLibPath, sourcePath);
                if (file.exists()) {
                    return file;
                }
                continue; // otherwise try the next variant in the source path
            }

            String sourceCrc = crc(cinput);
            if (dirName == null) {
                dirName = sourceCrc;
                System.out.println("Setting up temp directory as CRC32 code = " + dirName);
            }

            File extractedDir = new File(System.getProperty(JAVA_IO_TMPDIR) + WORK_DIR + System.getProperty(USER_NAME)
                + OS_SEPARATOR + dirName);
            System.out.println("Extracted directory = " + extractedDir.getAbsolutePath());
            File extractedFile = new File(extractedDir, new File(sourcePath).getName());
            System.out.println("Extracted File = " + extractedFile.getAbsolutePath());
            String extractedCrc = null;
            if (extractedFile.exists()) {
                try {
                    extractedCrc = crc(new FileInputStream(extractedFile));
                }
                catch (FileNotFoundException ignored) {
                    System.out.println("An error occurred during extractingCrc");
                    ignored.printStackTrace();
                }
            } else {
                System.out.println("extracted file doesn't exist! Path=" + extractedFile.getAbsolutePath());
            }

            // if file doesn't exist or the CRC doesn't match, extract it to the temp dir
            if ((extractedCrc == null) || !extractedCrc.equals(sourceCrc)) {
                try {
                    InputStream input = readFile(sourcePath);
                    extractedDir.mkdirs();
                    System.out.println("Created directory = " + extractedDir.getAbsolutePath());
                    FileOutputStream output = new FileOutputStream(extractedFile);
                    byte[] buffer = new byte[4096];
                    while (true) {
                        int length = input.read(buffer);
                        if (length == -1) {
                            break;
                        }
                        output.write(buffer, 0, length);
                    }
                    input.close();
                    output.close();
                }
                catch (IOException ex) {
                    throw new RuntimeException("Error extracting file: " + sourcePath, ex);
                }
            }
            return extractedFile;
        }
        throw new FileNotFoundException("Unable to find shared lib for '" + libraryName + "'");
    }

}
