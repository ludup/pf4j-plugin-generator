package com.jadaptive.plf4j.plugins;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;

/**
 * Generates the dependencies properties file
 * 
 * @version $Id: $
 * @goal generate-plugin
 * @phase package
 * @requiresDependencyResolution runtime
 * @description Generates the dependencies for plugin generation
 */
public class GeneratePluginMojo extends AbstractMojo {

	protected static final String SEPARATOR = "/";

	/**
	 * The maven project.
	 * 
	 * @parameter expression="${project}"
	 * @required
	 * @readonly
	 */
	protected MavenProject project;
		
	/**
	 * @component
	 */
	protected ArtifactFactory factory;

	@SuppressWarnings("unchecked")
	public void execute() throws MojoExecutionException, MojoFailureException {
		
		
		listDependencies(project);
		
		getLog().info(project.getBasedir().getAbsolutePath());
		getLog().info(project.getExecutionProject().getBasedir().getAbsolutePath());

		MavenProject rootProject = project.getParent();
		
		getLog().info("Building dependencies for plugin " + project.getArtifactId());
		
		try {

			File storeTarget = new File(rootProject.getBasedir(),
					"target"
					+ File.separator
					+ "dist"		
					+ File.separator
					+ "plugins" 
					+ File.separator
					+ project.getArtifactId() 
					+ "-"
					+ project.getVersion() 
					+ ".zip");
			
			storeTarget.getParentFile().mkdirs();
			
			File targetDir = new File(project.getBasedir(), "target");
			File targetLib = new File(targetDir, "lib");
			File targetClasses = new File(targetDir, "classes");
			
			File extensionDef = new File(targetDir, "plugin-def"
					+ File.separator + project.getArtifactId());
			extensionDef.mkdirs();

			File zipfile = new File(extensionDef, project.getArtifactId() + "-"
					+ project.getVersion() + ".zip");
			
			ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipfile));
			Set<String> addedPaths = new HashSet<String>();
			
			generateProperties(zip);
			
			zipAndRecurse(targetClasses, targetDir, zip);

			for (Artifact a : ((Set<Artifact>)project.getArtifacts())) {

				if(a.getScope().equalsIgnoreCase("system")) {
					continue;
				}
				
				if(a.getScope().equalsIgnoreCase("test")) {
					continue;
				}
				
				File resolvedFile = a.getFile();
				
				if (!resolvedFile.exists()) {
					getLog().warn(
							resolvedFile.getAbsolutePath()
									+ " does not exist!");
					continue;
				}
				if (resolvedFile.isDirectory()) {
					getLog().warn(
							resolvedFile.getAbsolutePath()
									+ " is a directory");
					continue;
				}

				addPluginArtifact(resolvedFile, zip, addedPaths, targetLib);
				
			}

			zip.close();
			
			// Generate an MD5 hash
			File md5File = new File(extensionDef, project.getArtifactId() + "-"	+ project.getVersion() + ".md5");
			FileUtils.fileWrite(md5File.getAbsolutePath(), DigestUtils.md5Hex(new FileInputStream(zipfile)));
			
			getLog().info("MD5 sum value is " + IOUtil.toString(new FileInputStream(md5File)));
			
			getLog().info("Copying archive to local store " + storeTarget.getAbsolutePath());
			
			FileUtils.copyFile(zipfile, storeTarget);
			
		} catch (Exception e) {
			getLog().error(e);
			throw new MojoExecutionException(
					"Unable to create dependencies file: " + e, e);
		} 
	}
	
	private void generateProperties(ZipOutputStream zip) throws MojoFailureException, IOException {
		
		String pluginId = project.getModel().getProperties().getProperty("plugin.id");
		String pluginClass = project.getModel().getProperties().getProperty("plugin.class", "");
		String pluginVersion = project.getModel().getProperties().getProperty("plugin.version", "");
		String pluginProvider = project.getModel().getProperties().getProperty("plugin.provider", "");
		String pluginDependencies = project.getModel().getProperties().getProperty("plugin.dependencies", "");
		
		if(StringUtils.isEmpty(pluginId)) {
			throw new MojoFailureException("Missing plugin.id property");
		}
		Properties properties = new Properties();
		properties.put("plugin.id", pluginId);
		properties.put("plugin.class", pluginClass);
		properties.put("plugin.version", pluginVersion);
		properties.put("plugin.provider", pluginProvider);
		properties.put("plugin.dependencies", pluginDependencies);
		
		
		ZipEntry e = new ZipEntry("plugin.properties");
		zip.putNextEntry(e);
		properties.store(zip, "Generated by PF4J Plugin Generator by JADAPTIVE Limited");
		zip.closeEntry();
		
		File pluginProperties = new File(project.getBasedir(), "plugin.properties");
		OutputStream out = new FileOutputStream(pluginProperties);
		try {
			properties.store(out, "Generated by PF4J Plugin Generator by JADAPTIVE Limited");
		} finally {
			out.close();
		}
	}
	
	private void addPluginArtifact(File resolvedFile, ZipOutputStream zip,  Set<String> addedPaths, File targetLib) throws IOException {
		
		getLog().info(
				"Adding " + resolvedFile.getName()
						+ " to plugin");
		
		String path = "lib/" + resolvedFile.getName();

		if(addedPaths.contains(path)) {
			getLog().info("Already added " + path);
			return;
		}
		
		addedPaths.add(path);
		
		ZipEntry e = new ZipEntry(path);
		
		zip.putNextEntry(e);

		IOUtil.copy(new FileInputStream(resolvedFile), zip);

		zip.closeEntry();
		
		
		File artifactFile = new File(targetLib, resolvedFile.getName());
		FileUtils.copyFile(resolvedFile, artifactFile);
	}

	@SuppressWarnings("unchecked")
	private void listDependencies(MavenProject project) {
		getLog().info(project.getArtifactId() + " has the following dependencies");
		
		for(Artifact a : ((Set<Artifact>)project.getArtifacts())) {
			getLog().info(a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion());
		}
	}

	private void zipAndRecurse(File file, File parent, ZipOutputStream zip) throws FileNotFoundException, IOException {
		
		if(file.isDirectory()) {
			for(File child : file.listFiles()) {
				zipAndRecurse(child, parent, zip);
			}
		} else {
			String name = file.getAbsolutePath().replace(parent.getAbsolutePath(), "");
			if(name.startsWith("/")) {
				name = name.substring(1);
			}
			getLog().info("Adding " + name + " to plugin");
			zip.putNextEntry(new ZipEntry(name));
			IOUtil.copy(new FileInputStream(file), zip);
			zip.closeEntry();
		}
	}


}