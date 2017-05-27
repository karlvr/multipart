package com.xk72.multipart;

import java.io.Serializable;

public class MultipartHttpParameter implements Serializable {
	
	private static final long serialVersionUID = 1L;

	private String name, value;

	public MultipartHttpParameter(String name, String value) {
		super();
		this.name = name;
		this.value = value;
	}

	public String getName() {
		return name;
	}

	public String getValue() {
		return value;
	}
	
}
