package com.xk72.multipart;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

public class MultipartParserTest {

	@Test
	public void testEmptyRequest() throws IOException {
	    final MultipartParser parser = new MultipartParser();
	    parser.parse(null, new byte[0], "UTF-8");
	}

	@Test(expected = IOException.class)
	public void testInvalidRequest() throws IOException {
		StringWriter writer = new StringWriter();
		PrintWriter out = new PrintWriter(writer);
		out.println("Hi");
		
		testPOST(writer.getBuffer().toString(), "--BOUNDARY");
	}
	
	@Test
	public void testBasicRequest() throws IOException {
		StringWriter writer = new StringWriter();
		PrintWriter out = new PrintWriter(writer);
		out.println("--BOUNDARY");
		out.println("Content-Disposition: form-data; name=\"test\"");
		out.println();
		out.println("Hi");
		out.println("--BOUNDARY--");
		
		MultipartParser m = testPOST(writer.getBuffer().toString(), "--BOUNDARY");
		
		Assert.assertEquals("Hi", m.getParameter("test"));
	}
	
	/**
	 * Test to confirm that we can handle multi-part parts without a name.
	 * @throws IOException
	 */
	@Test
	public void testBrokenRequestMissingName() throws IOException {
		StringWriter writer = new StringWriter();
		PrintWriter out = new PrintWriter(writer);
		out.println("--BOUNDARY");
		out.println("Content-Disposition: form-data");
		out.println();
		out.println("Hi 1");
		out.println("--BOUNDARY");
		out.println("Content-Disposition: form-data; name=\"test2\"");
		out.println();
		out.println("Hi 2");
		out.println("--BOUNDARY--");
		
		MultipartParser m = testPOST(writer.getBuffer().toString(), "--BOUNDARY");
		
		Assert.assertEquals("Hi 2", m.getParameter("test2"));
	}
	
	private MultipartParser testPOST(String body, String boundary) throws IOException {
	    final MultipartParser parser = new MultipartParser();
	    parser.parse(boundary, body.getBytes(StandardCharsets.UTF_8), "UTF-8");
	    return parser;
	}
	
}
