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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jadaptive.plf4j.plugins.PluginInfo.PluginRelease;

@Mojo(name = "generate-plugin", requiresProject = false, defaultPhase = LifecyclePhase.INSTALL, requiresDependencyCollection = ResolutionScope.RUNTIME, requiresDependencyResolution = ResolutionScope.RUNTIME)
public class GeneratePluginMojo extends AbstractMojo {

	protected static final String SEPARATOR = "/";

	@Component
	protected MavenProject project;

	@Parameter(defaultValue = "true")
	protected Boolean copyDependencies = true;

	@Parameter(defaultValue = "true")
	protected Boolean useLinksForCopyDependencies = true;
	
	@Parameter(defaultValue = "target/lib")
	protected String copyDependenciesPath = "target/lib";
	
	@Parameter(defaultValue = "true")
	protected Boolean createRepository = true;
	
	@Parameter(defaultValue = "target/repository")
	protected String repositoryPath = "target/repository";
	
	@Parameter(defaultValue = "false")
	protected Boolean artifactIsPlugin = true;

	@Parameter(defaultValue = "false")
	protected Boolean includeArtifact = false;

	@Parameter(defaultValue = "lib")
	protected String artifactPath = "lib";
	
	@Parameter(defaultValue = "true")
	protected Boolean includeClasses = true;
	
	@Parameter(defaultValue = "classes")
	protected String classesPath = "classes";
	
	@Parameter(defaultValue = "true")
	protected Boolean includeDependencies = true;
	
	@Parameter(defaultValue = "lib")
	protected String dependencyPath = "lib";

	@SuppressWarnings("unchecked")
	public void execute() throws MojoExecutionException, MojoFailureException {
		
		
		listDependencies(project);

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
			File targetClasses = new File(targetDir, classesPath);
			
			File copyDependenciesFolder = new File(project.getBasedir(), copyDependenciesPath);
			
			File extensionDef = new File(targetDir, "plugin-def"
					+ File.separator + project.getArtifactId());
			extensionDef.mkdirs();

			File zipfile = new File(extensionDef, project.getArtifactId() + "-"
					+ project.getVersion() + ".zip");
			
			Properties properties = generateProperties();
			addPropertiesToProject(properties);
			
			if(!artifactIsPlugin) {
				ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipfile));
				Set<String> addedPaths = new HashSet<String>();
				
				addPropertiesToZip(zip, properties);
				
				if(includeClasses) {
					zipAndRecurse(targetClasses, targetDir, zip);
				}
				
				if(includeDependencies) {
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
		
						addPluginArtifact(resolvedFile, zip, addedPaths, dependencyPath);
					
						if(copyDependencies) {
							File artifactFile = new File(copyDependenciesFolder, resolvedFile.getName());
							if(useLinksForCopyDependencies) {
								Path artifactPath = artifactFile.toPath();
								Files.deleteIfExists(artifactPath);
								Files.createDirectories(artifactPath.getParent());
								Files.createSymbolicLink(artifactPath, resolvedFile.toPath());
							}
							else {
								FileUtils.copyFile(resolvedFile, artifactFile);
							}
						}
					}
				}
				
				if(includeArtifact) {
					addPluginArtifact(project.getArtifact().getFile(), zip, addedPaths, artifactPath);
				}
	
				zip.close();
			
			}
			
			if(createRepository) {
				
				if(artifactIsPlugin) {
					try(InputStream in = new FileInputStream(project.getArtifact().getFile())) {
						addToRepository(project.getArtifact().getFile(), 
								DigestUtils.sha512Hex(in), 
									new File(repositoryPath));
					}
				} else {
					try(InputStream in = new FileInputStream(zipfile)) {
						addToRepository(zipfile,
								DigestUtils.sha512Hex(in),
									new File(repositoryPath));
					}
				}
			}
			
		} catch (Exception e) {
			getLog().error(e);
			throw new MojoExecutionException(
					"Unable to create dependencies file: " + e, e);
		} 
	}
	
	private Properties generateProperties() throws MojoFailureException, IOException {
		
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
		
		return properties;
		
	}
	
	private void addPropertiesToZip(ZipOutputStream zip, Properties properties) throws IOException {
		ZipEntry e = new ZipEntry("plugin.properties");
		zip.putNextEntry(e);
		properties.store(zip, "Generated by PF4J Plugin Generator by JADAPTIVE Limited");
		zip.closeEntry();
		
	}
	
	private void addPropertiesToProject(Properties properties) throws IOException {
		File pluginProperties = new File(project.getBasedir(), "plugin.properties");
		OutputStream out = new FileOutputStream(pluginProperties);
		try {
			properties.store(out, "Generated by PF4J Plugin Generator by JADAPTIVE Limited");
		} finally {
			out.close();
		}
	}
	
	private void addPluginArtifact(File resolvedFile, ZipOutputStream zip,  Set<String> addedPaths, String zipPath) throws IOException {
		
		getLog().info(
				"Adding " + resolvedFile.getName()
						+ " to plugin");
		
		String path = (StringUtils.isNotEmpty(zipPath) 
					? zipPath + "/" : "")
						+ resolvedFile.getName();

		if(addedPaths.contains(path)) {
			getLog().info("Already added " + path);
			return;
		}
		
		addedPaths.add(path);
		
		ZipEntry e = new ZipEntry(path);
		
		zip.putNextEntry(e);

		try(InputStream in = new FileInputStream(resolvedFile)) {
			IOUtil.copy(in, zip);
		}

		zip.closeEntry();

	}

	@SuppressWarnings("unchecked")
	private void listDependencies(MavenProject project) {
		getLog().info("------- " + project.getArtifactId() + " has the following dependencies");
		
		for(Artifact a : ((Set<Artifact>)project.getArtifacts())) {
			getLog().info(a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion());
		}
		
		getLog().info("------- End of dependencies");
	}

	private void zipAndRecurse(File file, File parent, ZipOutputStream zip) throws FileNotFoundException, IOException {
		
		if(!file.exists()) {
			getLog().warn(file.getName() + " does not exist");
			return;
		}
		
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
			
			try(InputStream in = new FileInputStream(file)) {
				IOUtil.copy(in, zip);
			}
			
			zip.closeEntry();
		}
	}


	private void addToRepository(File zipfile, String sha512Hash, File repositoryPath) throws IOException {
	
		Gson json = new GsonBuilder().registerTypeAdapter(Date.class, new LenientDateTypeAdapter()).create();
		List<PluginInfo> pluginsInfo = new ArrayList<>();
		File pluginsFile = new File(repositoryPath, "plugins.json");
		getLog().info("Writing repository to " + pluginsFile.getAbsolutePath());
		pluginsFile.getParentFile().mkdirs();

		if(pluginsFile.exists()) {
			try(InputStream in = new FileInputStream(pluginsFile)) {
				 pluginsInfo.addAll(Arrays.asList(
						 json.fromJson(new InputStreamReader(in), PluginInfo[].class)));
			}
		}
		
		String pluginId = project.getProperties().getProperty("plugin.id");
		String pluginDescription = project.getDescription();
		String pluginName = project.getName();
		String pluginProjectUrl = project.getProperties().getProperty("plugin.projectUrl");
		String pluginProvider = project.getProperties().getProperty("plugin.provider");
		String pluginDependencies = project.getProperties().getProperty("plugin.dependencies");
		
		PluginInfo thisPlugin = null;
		for(PluginInfo pluginInfo : pluginsInfo) {
			if(pluginInfo.id.equals(pluginId)) {
				thisPlugin = pluginInfo;
				break;
			}
		}
		
		if(Objects.isNull(thisPlugin)) {
			thisPlugin = new PluginInfo();
			thisPlugin.id = pluginId;
			thisPlugin.description = pluginDescription;
			thisPlugin.name = pluginName;
			thisPlugin.projectUrl = pluginProjectUrl;
			thisPlugin.provider = pluginProvider;
			thisPlugin.releases = new ArrayList<>();
			thisPlugin.setRepositoryId("testing");
			pluginsInfo.add(thisPlugin);
		}
		
		PluginRelease thisRelease = null;
		for(PluginRelease release : thisPlugin.releases) {
			if(release.version.equals(project.getVersion())) {
				thisRelease = release;
				break;
			}
		}
		
		if(Objects.isNull(thisRelease)) {
			thisRelease = new PluginRelease();
		}
		
		thisRelease.date = new Date();
		thisRelease.requires = pluginDependencies;
		thisRelease.sha512sum = sha512Hash;
		thisRelease.url = pluginId + "/" + zipfile.getName();
		thisRelease.version = project.getVersion();

		thisPlugin.releases.add(thisRelease);
		
		File dir = new File(repositoryPath, pluginId);
		dir.mkdirs();
		FileUtils.copyFileToDirectory(zipfile, dir);
		FileUtils.fileWrite(pluginsFile.getAbsolutePath(), json.toJson(pluginsInfo));
		
	}
	
	
}