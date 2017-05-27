/*
 * MacBinaryDataForkOutputStream.java
 *
 * Created on 9 July 2002, 08:35
 */

package com.xk72.multipart;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Write a MacBinary file to this OutputStream and only the data fork of the
 * file will be written to the underlying OutputStream. Write any other file and
 */
public class MacBinaryDataForkOutputStream extends OutputStream {
	private static final int MACBINARY_HEADER_SIZE = 128;

	private byte[] header = new byte[MACBINARY_HEADER_SIZE];

	private int headerLength = 0;

	private int dataforkLength;

	private OutputStream out;

	/** Creates new MacBinaryDataForkOutputStream */
	public MacBinaryDataForkOutputStream(OutputStream out) {
		this.out = out;
	}

	@Override
	public void write(int c) throws java.io.IOException {
		if (headerLength < MACBINARY_HEADER_SIZE) {
			header[headerLength++] = (byte) c;

			if (headerLength == MACBINARY_HEADER_SIZE) {
				verifyHeader();
			}
		} else if (dataforkLength > 0) {
			out.write(c);
			dataforkLength--;
		}
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		if (headerLength < MACBINARY_HEADER_SIZE) {
			super.write(b, off, len); // to process header through the other
										// write method
		} else if (dataforkLength > 0) {
			len = Math.min(dataforkLength, len);
			out.write(b, off, len);
			dataforkLength -= len;
		}
	}

	private void verifyHeader() throws IOException {
		if (header[0] != 0) {
			throw new IOException("Bad MacBinary header (0)");
		}

		int nameLength = header[1];
		if (nameLength < 0 || nameLength > 63) {
			throw new IOException("Bad MacBinary header (1)");
		}

		// Check that last char in name is not null
		if (header[1 + nameLength] == 0) {
			throw new IOException("Bad MacBinary header (2)");
		}

		if (header[74] != 0) {
			throw new IOException("Bad MacBinary header (3)");
		}
		if (header[82] != 0) {
			throw new IOException("Bad MacBinary header (4)");
		}

		// Get data fork length
		dataforkLength = (header[83] & 0xff) << 24 | (header[84] & 0xff) << 16 | (header[85] & 0xff) << 8 | (header[86] & 0xff);
	}

	@Override
	public void flush() throws IOException {
		out.flush();
	}

	@Override
	public void close() throws IOException {
		out.close();
	}
}
