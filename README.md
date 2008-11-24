ssTFTP
========
Open-source implementation of a Trivial File Transfer Protocol (TFTP)
server and client in JAVA.

Description
-----------
ssTFTP is a Trivial File Transfer Protocol (TFTP) client and server
implementations in JAVA. It was developed for a class in 2008, which I
decided to revive and improve.

The current version (v0.2 beta) implements the TFTP client and server according
to the standard RFC 1350. TFTP Option Extension, according standard RFC 2347,
is implemented:
   -> TFTP Blocksize Option (RFC 2348)

For more info, please check CHANGELOG file.

Requirements
------------
* Java 7
* Maven (for building)

Build ssTFTP
--------------
ssTFTP is a standard Maven project. Simply run the following command
from the project root directory:

    mvn clean install

On the first build, Maven will download all the dependencies from the
internet and cache them in the local repository (`~/.m2/repository`), which
can take a considerable amount of time. Subsequent builds will be faster.

Where to get help
-----------------
You can ask me directly by email or create an issue in
[GitHub repository](https://github.com/cguimaraes/ssTFTP).

Contribution guidelines
-----------------------
First make sure that your patch follows these rules:

1. It works!! :)
2. No trailing white spaces.
3. Keep the code style consistent.
4. Use tabs and no spaces, except for alignment where you should use spaces
   instead. Tab width is 4.

After that you can create a pull request in
[GitHub repository](https://github.com/cguimaraes/ssTFTP).

Contributor list
----------------
	Carlos Guimarães <carlos.em.guimaraes@gmail.com>

External Contributors:

	You can be the first one :)
