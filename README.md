# Multipart

A parser for `multipart/form-data` requests that does not depend upon any `javax.servlet` classes. This makes it suitable for use with AWS Lambda,
or another situation where you do not have a servlet stack, but do have access to the contents of a multipart POST.
