/*
 * MultipartHttpServletRequest.java
 *
 * Created on 26 June 2002, 14:16
 */

package com.xk72.multipart;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parse <code>multipart/form-data</code> POSTs, including parameters and
 * attachments.
 * <p>
 * MultipartParser requires the boundary string, parsed from the <code>Content-Type</code>
 * request header, the bytes of the body, and an optional charset to use to decode
 * strings.
 * <p>
 * <b>Attachments</b><br>
 * MultipartParser can either store attachments in memory, in byte
 * arrays, or as temporary files on the file system. The later is recommended if
 * large files may be uploaded. Temporary files will be created in the directory
 * specified in the constructor, using the File.createTempFile method. The
 * deleteOnExit() method will be called on the File so that it is deleted if not
 * moved or renamed (although this is not reliable if the JVM is abnormally
 * terminated). Therefore you should always rename, move or delete attachments.
 * <p>
 * If temporary files are used then the memory usage is kept to a minimum as the
 * InputStream is read in one pass, in 8KB chunks, and written to the file.
 * <p>
 * The attachment is accessed using the {@link #getAttachment(String)} method, which
 * returns a {@link MultipartHttpAttachment} object containing information about the
 * attachment. The attachment path (from the client) is also available using
 * {@link MultipartHttpAttachment#getClientPath()} and {@link MultipartHttpAttachment#getClientFileName()}.
 */
public class MultipartParser {
	private static final int BUFSIZ = 8192;

    private static Logger logger = Logger.getLogger(MultipartParser.class.getName());

	private static final String FORM_DATA = "form-data";
	private static final String CONTENT_DISPOSITION = "Content-Disposition";
	private static final String CONTENT_TYPE = "Content-Type";
	private static final String MACBINARY_CONTENT_TYPE = "application/x-macbinary";

	private Map<String, Object> parameters = new HashMap<>();
	private List<MultipartHttpParameter> orderedParameters = new ArrayList<>();

	private Map<String, Object> attachments = new HashMap<>();

	private boolean saveFiles;
	private String uploadPath;

	/**
	 * Whether uploaded MacBinary files should be decoded to just the datafork.
	 * Defaults to true. You may want to turn this off if you want Mac users to
	 * be able to upload and subsequently download Mac applications (or any Mac
	 * file that has a resource fork).
	 */
	private boolean decodeMacBinary = true;

	/**
	 * Create a new parser that stores attachments in memory.
	 */
	public MultipartParser() {
		
	}

	/**
	 * Create a new parser.
	 * 
	 * @param uploadPath
	 *            If the uploadPath is not null, it specifies an absolute path
	 *            to a directory where temporary files can be created to hold
	 *            attachment data.
	 *            If uploadPath is null, then attachments will be kept in memory.
	 */
	public MultipartParser(String uploadPath) {
		this.uploadPath = uploadPath;
		this.saveFiles = uploadPath != null;
	}

	/**
	 * Parses the request InputStream for multipart form-data. If no such data
	 * exists, this method returns. Called by the constructor.
	 */
	public boolean parse(final String boundary, byte[] body, String encoding) throws IOException {
	    if (boundary == null || boundary.length() == 0) {
	        return false;
	    }
	    
		final String finalBoundary = boundary + "--";

		final LineInputStream in = new LineInputStream(new ByteArrayInputStream(body));

		/* Check first line is boundary */
		String line = readLine(in, null);
		while (line != null && line.length() == 0)
			line = readLine(in, null);
		if (line == null)
			return false;
		if (!line.equals(boundary)) {
			throw new IOException("POST data doesn't start with boundary, starts with \"" + line + "\", boundary is \"" + boundary + "\"");
		}

		/* Main loop always starts with a boundary line */
		while (line != null) {
			if (line.equals(finalBoundary))
				break;
			if (!line.equals(boundary)) {
				throw new IOException("Boundary expected but not found");
			}

			String name = null;
			String filename = null;
			String contentType = null;

			/* Read headers, terminated by a blank line */
			line = readLine(in, null);
			while (line != null && line.length() > 0) {
				String header = line;
				line = readLine(in, null);
				while (line != null && line.length() != 0 && line.charAt(0) <= 32) {
					header += line;
					line = readLine(in, null);
				}

				/* Process header */
				int i = header.indexOf(':');
				if (i != -1) {
					String key = header.substring(0, i).toLowerCase();
					String value = header.substring(i + 2);

					/* Content Disposition */
					if (key.equalsIgnoreCase(CONTENT_DISPOSITION)) {
						if (value.toLowerCase().startsWith(FORM_DATA)) {
							name = parameterValue("name", value);
							filename = parameterValue("filename", value);
						}
					} else if (key.equalsIgnoreCase(CONTENT_TYPE)) {
						contentType = value;
					}
				}
			}

			if (name == null) {
				/* If there's no name then we can't do anything with it. Read until the next boundary, effectively discarding this part. */
				line = readLine(in, encoding);
				while (line != null && !line.equals(boundary) && !line.equals(finalBoundary)) {
					line = readLine(in, encoding);
				}
			} else if (filename == null) {
				/* Read parameter content, use specified encoding */
				StringBuilder value = new StringBuilder();
				line = readLine(in, encoding);
				value.append(line);

				line = readLine(in, encoding);
				while (line != null && !line.equals(boundary) && !line.equals(finalBoundary)) {
					value.append('\n');
					value.append(line);

					line = readLine(in, encoding);
				}

				if (logger.isLoggable(Level.FINE)) {
					logger.fine("Adding parameter \"" + name + "\" with value \"" + value + "\"");
				}
				addParameter(name, value.toString());
			} else {
				/* Read attachment content */
				File file = null;
				ByteArrayOutputStream buffer = null;
				OutputStream out;

				if (saveFiles) {
					file = File.createTempFile("xk72webparts", null, uploadPath != null ? new File(uploadPath) : null);
					file.deleteOnExit();
					out = new FileOutputStream(file);
				} else {
					buffer = new ByteArrayOutputStream();
					out = buffer;
				}

				if (decodeMacBinary && contentType != null && contentType.equals(MACBINARY_CONTENT_TYPE)) {
					out = new MacBinaryDataForkOutputStream(out);
				}

				/*
				 * Buffers are used to read the attachment in chunks The
				 * tail is used as when we find the boundary, we have to
				 * remove the newline from the line before
				 */
				byte[] buf = new byte[BUFSIZ];
				byte[] tail = new byte[2];
				int tailSize = 0;

				/*
				 * Set the line to null, it will get set to the boundary
				 * when one is found.
				 */
				line = null;

				int n = in.readLine(buf, 0, buf.length);
				long len = 0;

				while (n != -1) {
					/*
					 * Check if the line is of a length that might make it a
					 * boundary that is <= the length of the final boundary
					 * + 2 (for \r\n)
					 */
					if (n <= finalBoundary.length() + 2) {
						line = new String(buf, 0, n).trim();
						if (line.equals(boundary) || line.equals(finalBoundary)) {
							/* Don't write out tail */
							break;
						} else {
							line = null;
						}
					}

					/* Tail is part of attachment, write it out */
					out.write(tail, 0, tailSize);
					len += tailSize;

					if (n > 1) {
						out.write(buf, 0, n - 2);
						len += n - 2;
						tail[0] = buf[n - 2];
						tail[1] = buf[n - 1];
						tailSize = 2;
					} else if (n == 1) {
						tail[0] = buf[n - 1];
						tailSize = 1;
					} else {
						tailSize = 0;
					}

					n = in.readLine(buf, 0, buf.length);
				}

				out.flush();
				out.close();

				MultipartHttpAttachment attachment;
				if (len > 0) {
					attachment = new MultipartHttpAttachment();
					attachment.setClientPath(filename);
					attachment.setContentType(contentType);
					if (saveFiles) {
						attachment.setFile(file);
					} else {
						attachment.setBytes(buffer.toByteArray());
					}

					if (logger.isLoggable(Level.FINE)) {
						logger.fine("Adding attachment \"" + name + "\" with client filename \"" + filename + "\"");
					}
				} else {
					/*
					 * Add a null attachment so that we at least have a
					 * record for it having been part of the request. And
					 * then in an array of attachments we'll have the
					 * appropriate nulls in the array for ones that weren't
					 * actually attached. Like we do for text inputs, where
					 * we'll have empty strings in the array
					 */
					attachment = null;
					if (saveFiles) {
						file.delete();
					}
				}
				addParameter(name, filename);
				addAttachment(name, attachment);
			}
		}
		
		return true;
	}

	/**
	 * Given a <code>Content-Type</code> header value, returns true if the request is a
	 * multipart request, and can be parsed with this class.
	 * @param contentType
	 * @return
	 */
	public static boolean isMultipartRequest(String contentType) {
		if (contentType == null)
			return false;

		String boundaryMarker = "boundary=";
		int i = contentType.indexOf(boundaryMarker);
		if (i == -1) {
			return false;
		} else {
			return true;
		}
	}

	/**
	 * Given a <code>Content-Type</code> header value, returns the <code>boundary</code> value
	 * to use with the {@link #parse(String, byte[], String)} method, or <code>null</code> if the
	 * request was not a multipart request.
	 * @param contentType
	 * @return
	 */
	public static String parseBoundary(final String contentType) {
		if (contentType == null)
			return null;

		String boundaryMarker = "boundary=";
		int i = contentType.indexOf(boundaryMarker);
		if (i == -1) {
			return null;
		} else {
			int j = contentType.indexOf(';', i);
			if (j == -1)
				j = contentType.length();
			String boundary = contentType.substring(i + boundaryMarker.length(), j).trim();
			if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
				boundary = boundary.substring(1, boundary.length() - 1);
			}
			return "--" + boundary;
		}
	}

    public boolean isDecodeMacBinary() {
        return decodeMacBinary;
    }

    public void setDecodeMacBinary(boolean decodeMacBinary) {
        this.decodeMacBinary = decodeMacBinary;
    }

    /**
     * Returns an {@link Iterator} of the multipart parameter names.
     */
    public Iterator<String> getParameterNames() {
        return parameters.keySet().iterator();
    }
    
    public Iterator<MultipartHttpParameter> getOrderedParameters() {
        return orderedParameters.iterator();
    }
    
    /**
     * Returns the value of the parameter in the multipart post.
     */
    public String getParameter(String name) {
        Object o = parameters.get(name);
        if (o == null)
            return null;

        if (o instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> list = ((List<String>) o);
            return list.get(0);
        } else {
            return (String) o;
        }
    }

    /**
     * Returns the values of the parameter in the multipart post.
     */
    public String[] getParameterValues(String name) {
        Object o = parameters.get(name);
        if (o == null)
            return null;

        if (o instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) o;
            String[] ray = new String[list.size()];
            return list.toArray(ray);
        } else {
            return new String[] { (String) o };
        }
    }

    /**
     * Returns an {@link Iterator} of the attachment names.
     */
    public Iterator<String> getAttachmentNames() {
        return attachments.keySet().iterator();
    }

    /**
     * Returns the attachment object for the attachment with the given name.
     */
    public MultipartHttpAttachment getAttachment(String name) {
        Object o = attachments.get(name);
        if (o == null)
            return null;

        if (o instanceof List) {
            @SuppressWarnings("unchecked")
            List<MultipartHttpAttachment> list = ((List<MultipartHttpAttachment>) o);
            return list.get(0);
        } else {
            return (MultipartHttpAttachment) o;
        }
    }

    /**
     * Returns the attachment objects for the attachments with the given name.
     */
    public MultipartHttpAttachment[] getAttachmentValues(String name) {
        Object o = attachments.get(name);
        if (o == null)
            return null;

        if (o instanceof List) {
            @SuppressWarnings("unchecked")
            List<MultipartHttpAttachment> al = (List<MultipartHttpAttachment>) o;
            MultipartHttpAttachment[] ray = new MultipartHttpAttachment[al.size()];
            return al.toArray(ray);
        } else {
            return new MultipartHttpAttachment[] { (MultipartHttpAttachment) o };
        }
    }

    private void addParameter(String name, String value) {
        this.orderedParameters.add(new MultipartHttpParameter(name, value));
        
        if (!parameters.containsKey(name)) {
            parameters.put(name, value);
        } else {
            Object o = parameters.get(name);
            if (o instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> list = ((List<String>) o);
                list.add(value);
            } else {
                List<String> ray = new ArrayList<>();
                ray.add((String) o);
                ray.add(value);
                parameters.put(name, ray);
            }
        }
    }

    private void addAttachment(String name, MultipartHttpAttachment att) {
        if (!attachments.containsKey(name)) {
            attachments.put(name, att);
        } else {
            Object o = attachments.get(name);
            if (o instanceof List) {
                @SuppressWarnings("unchecked")
                List<MultipartHttpAttachment> list = ((List<MultipartHttpAttachment>) o);
                list.add(att);
            } else {
                List<MultipartHttpAttachment> ray = new ArrayList<MultipartHttpAttachment>();
                ray.add((MultipartHttpAttachment) o);
                ray.add(att);
                attachments.put(name, ray);
            }
        }
    }

    /**
     * InputStream subclass to read a line at a time into a byte buffer.
     */
	private static class LineInputStream extends FilterInputStream {
	    public LineInputStream(InputStream in) {
            super(in);
        }

        public int readLine(byte[] buf, int offset, int length) throws IOException {
	        if (length <= 0) {
	            return 0;
	        }
	        
	        int count = 0;
	        int c = read();

	        while (c != -1) {
	            buf[offset++] = (byte) c;
	            count++;
	            if (c == '\n' || count == length) {
	                break;
	            }
	            
	            c = read();
	        }
	        
	        return count > 0 ? count : -1;
	    }
	}

	/**
	 * Read a line from the input stream. This method doesn't convert from bytes
	 * to a string until the entire line has been read. This fixes a problem in
	 * the previous implementation where it could corrupt multibyte characters
	 * if they occurred on a buffer boundary.
	 * 
	 * @param in
	 * @param encoding
	 * @return
	 * @throws IOException
	 */
	private String readLine(LineInputStream in, String encoding) throws IOException {
		byte[] buf = new byte[1024];
		byte[] collected = new byte[1024];
		int collectedLength = 0;

		while (true) {
			int n = in.readLine(buf, 0, buf.length);
			if (n == -1) {
				/* EOF */
				break;
			}

			if (collectedLength + n > collected.length) {
				/* Enlarge collected buffer */
				byte[] newCollected = new byte[collected.length + 8192];
				System.arraycopy(collected, 0, newCollected, 0, collectedLength);
				collected = newCollected;
			}
			System.arraycopy(buf, 0, collected, collectedLength, n);
			collectedLength += n;

			if (collected[collectedLength - 1] == '\n') {
				/* EOL */
				break;
			}
		}

		if (collectedLength > 0) {
			if (collected[collectedLength - 1] == '\n') {
				collectedLength--;
				if (collectedLength > 0 && collected[collectedLength - 1] == '\r') {
					collectedLength--;
				}
			}
			if (encoding != null) {
				return new String(collected, 0, collectedLength, encoding);
			} else {
				return new String(collected, 0, collectedLength);
			}
		} else {
			return null;
		}
	}

	/**
	 * Extract parameters from the mime parameter string. This doesn't remove
	 * the backslashes in the filename like the JavaMail ContentDisposition or
	 * ParameterList classes.
	 */
	private String parameterValue(String name, String str) {
		/* Find the start of the parameter */
		int i = str.toLowerCase().indexOf(name.toLowerCase() + "=\"");
		if (i != -1) {
			/* Move to start of value */
			i += name.length() + 2;
			/* Find the end of value (checking for escaped quotes etc) */
			int j = str.indexOf("\"", i);
			while (j != -1) {
				if (str.charAt(j - 1) != '\\' && (str.length() == j + 1 || str.charAt(j + 1) == ';')) {
					break;
				}
				j = str.indexOf("\"", j + 1);
			}
			if (j != -1) {
				return str.substring(i, j);
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

}
