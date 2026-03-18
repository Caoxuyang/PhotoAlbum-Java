package com.photoalbum.model;

import java.util.Arrays;

/**
 * Data transfer object carrying binary photo file content and metadata.
 * Used to encapsulate data access within the service layer (CWE-1057).
 */
public class PhotoBinaryData {

    private final byte[] data;
    private final String originalFileName;
    private final String mimeType;

    public PhotoBinaryData(byte[] data, String originalFileName, String mimeType) {
        this.data = data == null ? null : Arrays.copyOf(data, data.length);
        this.originalFileName = originalFileName;
        this.mimeType = mimeType;
    }

    public byte[] getData() {
        return data == null ? null : Arrays.copyOf(data, data.length);
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public String getMimeType() {
        return mimeType;
    }
}
