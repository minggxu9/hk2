/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package com.sun.enterprise.tools.visualizer.hk2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * reads a wires XML file generated by the dependency-verifier and outputs a dot
 * file (Use <a href="http://www.graphviz.org/">GraphViz</a> to create images
 * from the dot file) that helps visualizing the wiring dependencies between
 * packages. The tool also supports viewing the dependencies of only a subset of
 * the packages.
 * 
 * @author Sivakumar Thyagarajan
 */
public class DotGenerator {
	private final static boolean DEBUG = false;

	PrintStream wireOut = null;
	GeneratorOptions options = null;

	/**
	 * An internal class holding all the command line options
	 * supported by this DotGenerator
	 * 
	 * @author Sivakumar Thyagarajan
	 */
	static class GeneratorOptions {
		// Accept XML files generated by HK2 dependency-verifier
		@Option(name = "-i", usage = "Input Wires XML file")
		public String input = "wires.xml";

		@Option(name = "-o", usage = "Output DOT file", required = true)
		public String output;

		@Option(name = "-m", usage = "Show only packages that contains the specified substring")
		public String match = "";// By default, match all.

		// receives other command line parameters than options
		@Argument
		public List<String> arguments = new ArrayList<String>();
	}
	
	/**
	 * A simple class representing all the information about a
	 * package that can be derived from the wires.xml
	 * 
	 * @author Sivakumar Thyagarajan
	 */
	class PackageInfo {
		String packageName, exportedBy = null;
		String[] importedBy = null;

		public PackageInfo(String packageName, String exportedBy,
				String[] importedBy) {
			this.packageName = packageName;
			this.exportedBy = exportedBy;
			this.importedBy = importedBy;
		}
	}
	
	public DotGenerator(GeneratorOptions go) throws Exception {
		this.options = go;
		wireOut = new PrintStream(new FileOutputStream(this.options.output));
		initXML();
		generate();
	}

	private Document doc = null;

	private void initXML() throws Exception {
		File file = new File(this.options.input);
		debug("file " + file);
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		doc = db.parse(file);
		doc.getDocumentElement().normalize();
	}

	private PackageInfo[] getPackageXML() throws IOException {
		NodeList pkgLst = doc.getElementsByTagName("Package");
		debug("Package count:" + pkgLst.getLength());
		List<PackageInfo> pkgInfos = new ArrayList<PackageInfo>();
		for (int i = 0; i < pkgLst.getLength(); i++) {
			Node pkgNode = pkgLst.item(i);
			if (pkgNode.getNodeType() == Node.ELEMENT_NODE) {
				Element packageElement = (Element) pkgNode;
				// exports
				NodeList ExportersList = packageElement
						.getElementsByTagName("Exporters");
				Element exporterElt = (Element) ExportersList.item(0);
				String exporter = ((Node) exporterElt.getChildNodes().item(0))
						.getNodeValue().trim();
				debug("Exporter : " + exporter);

				// importers
				NodeList importersList = packageElement
						.getElementsByTagName("Importers");
				Element importerElt = (Element) importersList.item(0);
				String importers = ((Node) importerElt.getChildNodes().item(0))
						.getNodeValue().trim();
				debug("Importers : " + importers);

				// Get package name and return PackageInfos
				String pkgName = packageElement.getAttribute("name").trim();
				debug("Package Name : " + pkgName);
				PackageInfo pkgInfo = new PackageInfo(pkgName, exporter,
						split(importers));
				pkgInfos.add(pkgInfo);
			}
		}
		return pkgInfos.toArray(new PackageInfo[] {});
	}

	private void generate() throws Exception {
		generateDotStart();
		PackageInfo[] pkgInfos = getPackageXML();
		for (PackageInfo pkgInfo : pkgInfos) {
			// Match if needed
			String matchString = this.options.match.trim();
			boolean matchNeeded = !matchString.isEmpty();
			if (matchNeeded) {
				if (pkgInfo.exportedBy.contains(matchString)) {
					generateDotEdge(pkgInfo.importedBy, pkgInfo.exportedBy,
							pkgInfo.packageName);
				}
			} else {
				generateDotEdge(pkgInfo.importedBy, pkgInfo.exportedBy,
						pkgInfo.packageName);
			}
		}
		generateDotEnd();
	}

	private void debug(String s) {
		if (DEBUG)
			System.err.println(s);
	}

	private void debug(String text, String s) {
		debug(text, new String[] { s });
	}

	private void debug(String text, String[] arr) {
		StringBuffer sb = new StringBuffer(text);
		for (String s : arr) {
			sb.append(s).append(" , ");
		}
		debug(sb.toString());
	}

	public static void main(String[] args) throws Exception {
		DotGenerator.GeneratorOptions options = new DotGenerator.GeneratorOptions();
		CmdLineParser parser = new CmdLineParser(options);
		try {
			parser.parseArgument(args);
		} catch (CmdLineException e) {
			System.err.println(e.getMessage());
			System.err.println("java -jar program-name.jar [options...] arguments...");
			parser.printUsage(System.err);
			return;
		}
		new DotGenerator(options);
	}

	// Generate the beginning of the dot file
	private void generateDotStart() {
		this.wireOut.println("digraph  wiring {");
		this.wireOut.println("node [color=grey, style=filled];");
		this.wireOut.println("node [fontname=\"Verdana\", size=\"30,30\"];");
		String date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,
				DateFormat.SHORT).format(new Date());
		StringBuffer footer = new StringBuffer();
		footer.append("graph [ fontname = \"Arial\", fontsize = 26,style = \"bold\", ");
		footer.append("label = \"\\nGlassFish v3 OSGi bundle wiring relationship diagram");
		if (!this.options.match.trim().isEmpty()) {
			footer.append("\\n Filter: " + this.options.match.trim()
					+ " bundles");
		}
		footer.append("\\nSun Microsystems");
		footer.append("\\n\\nDate: " + date + "\\n\", "
				+ "ssize = \"30,60\" ];");
		this.wireOut.println(footer.toString());
	}

	// Generate a Dot representation for each edge in the graph
	private void generateDotEdge(String[] importedBy, String exportedBy,
			String pkg) {
		if (importedBy.length == 0)
			return;
		for (String s : importedBy) {
			if (!s.equals(exportedBy)) { // remove self-loops for readability
				this.wireOut.println("\"" + s + "\" -> \"" + exportedBy
						+ "\" [label =\"" + pkg + "\"" + "]");
			}
		}
	}

	// End the dot file generation
	private void generateDotEnd() {
		this.wireOut.println("}");
	}

	// Utility class to split the importers representation (space separated) 
	//in wires.xml
	private String[] split(String s) {
		StringTokenizer st = new StringTokenizer(s);
		List<String> l = new ArrayList<String>();
		while (st.hasMoreTokens()) {
			l.add(st.nextToken());
		}
		return l.toArray(new String[] {});
	}
}
