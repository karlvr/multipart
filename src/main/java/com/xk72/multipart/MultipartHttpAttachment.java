/*
 * MultipartHttpAttachment.java
 *
 * Created on 26 June 2002, 16:23
 */

package com.xk72.multipart;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * MultipartHttpAttachment contains the attachment data and information.
 * <p>
 * The attachment data will either be stored in a temporary file (one that has
 * had {@link File#deleteOnExit()} called) or a byte array in this object. The mode chosen
 * depends on the {@link MultipartParser} configuration.
 * <p>
 * Use the {@link #isFile()} method to determine if a {@link File} or byte array was used.
 */
public class MultipartHttpAttachment {

	/** Holds value of property clientPath. */
	private String clientPath;

	/** Holds value of property name. */
	private String name;

	/** Holds value of property contentType. */
	private String contentType;

	/** Holds value of property file. */
	private File file;

	/** Holds value of property bytes. */
	private byte[] bytes;

	/** Creates new MultipartHttpAttachment */
	public MultipartHttpAttachment() {

	}

	/**
	 * Return an InputStream of the attachment data. This is a FileInputStream
	 * if the attachment is stored in a file, or a ByteArrayInputStream if the
	 * attachment is a byte array.
	 */
	public InputStream getInputStream() throws IOException {
		if (file != null) {
			return new FileInputStream(file);
		} else if (bytes != null) {
			return new ByteArrayInputStream(bytes);
		} else {
			return null;
		}
	}

	/**
	 * Returns true if the attachment is stored in a temporary file rather than
	 * a byte array.
	 */
	public boolean isFile() {
		return (file != null);
	}

	/**
	 * Deletes the source data for this attachment, whether it is a file or an
	 * in memory buffer.
	 */
	public void delete() {
		if (file != null) {
			file.delete();
			file = null;
		}
		if (bytes != null) {
			bytes = null;
		}
	}

	/**
	 * Delete the source data for this attachment when this object is finalised.
	 * That way we clean up after ourselves as the deleteOnExit() on the file
	 * doesn't seem to work well.
	 */
	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		delete();
	}

	/**
	 * Returns the suffix of the file as it was on the client. The suffix does
	 * not include the '.'. If there is no suffix then an empty string is
	 * returned.
	 */
	public String getClientFileSuffix() {
		String file = getClientFileName();
		int i = file.lastIndexOf('.');
		if (i != -1) {
			return file.substring(i + 1);
		} else {
			return "";
		}
	}

	/**
	 * Returns the file name of the file as it was on the client. This does not
	 * include the path (directories), if any. This method includes support for
	 * paths in unix, windows or mac style.
	 */
	public String getClientFileName() {
		// UNIX
		int lastSlash = clientPath.lastIndexOf('/');
		if (lastSlash != -1) {
			return clientPath.substring(lastSlash + 1);
		}
		// Windows
		lastSlash = clientPath.lastIndexOf('\\');
		if (lastSlash != -1) {
			return clientPath.substring(lastSlash + 1);
		}
		// Mac OS 9
		lastSlash = clientPath.lastIndexOf(':');
		if (lastSlash != -1) {
			return clientPath.substring(lastSlash + 1);
		}
		return clientPath;
	}

	public long getLength() {
		if (file != null)
			return file.length();
		else if (bytes != null)
			return bytes.length;
		else
			return 0;
	}

	/**
	 * The full path name of the attachment as it was on the client. This
	 * includes path separators in the style of the client, eg. unix path
	 * separators, windows or mac etc.
	 * 
	 * @return Value of property clientPath.
	 */
	public String getClientPath() {
		return clientPath;
	}

	/**
	 * Setter for property clientPath.
	 * 
	 * @param clientPath
	 *            New value of property clientPath.
	 */
	public void setClientPath(String clientPath) {
		this.clientPath = clientPath;
	}

	/**
	 * Getter for property name.
	 * 
	 * @return Value of property name.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Setter for property name.
	 * 
	 * @param name
	 *            New value of property name.
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns the mimeType of the attachment as reported by the client.
	 * 
	 * @return Value of property contentType.
	 */
	public String getContentType() {
		return contentType;
	}

	/**
	 * Setter for property contentType.
	 * 
	 * @param contentType
	 *            New value of property contentType.
	 */
	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	/**
	 * Returns the File object where the attachment is temporarily stored, if
	 * the attachment is stored as a file.
	 * 
	 * @return Value of property file.
	 */
	public File getFile() {
		return file;
	}

	/**
	 * Setter for property file.
	 * 
	 * @param file
	 *            New value of property file.
	 */
	public void setFile(File file) {
		this.file = file;
	}

	/**
	 * Returns the byte array where the attachment is stored, if the attachment
	 * is stored as a byte array.
	 * 
	 * @return Value of property bytes.
	 */
	public byte[] getBytes() {
		return bytes;
	}

	/**
	 * Setter for property bytes.
	 * 
	 * @param bytes
	 *            New value of property bytes.
	 */
	public void setBytes(byte[] bytes) {
		this.bytes = bytes;
	}

}
