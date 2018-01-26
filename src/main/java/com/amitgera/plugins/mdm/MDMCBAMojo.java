package com.amitgera.plugins.mdm;

/**
 * Copyright 2018 Amit Gera
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.apache.maven.archiver.PomPropertiesUtil;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.codehaus.plexus.util.FileUtils;

/**
 * Mojo to create cba using dependecie and the composite bundle
 * @author Amit Gera
 * 
 * @goal cba
 * @goalPrefix cba
 * @requiresDependencyResolution runtime
 */
public class MDMCBAMojo extends AbstractMojo {

	/**
	 * Work directory for temporary files generated during plugin execution.
	 */
	private String workDirectory;

	/**
	 * Output directory for the cba
	 *
	 * @parameter property="project.build.directory"
	 * @required
	 */
	private String outputDirectory;

	/**
	 * The location of the COMPOSITEBUNDLE.MF file
	 *
	 * @parameter property="basedir/META-INF/COMPOSITEBUNDLE.MF"
	 */
	private File compositeBundleManifestFile;

	/**
	 * The name of the cba file to generate
	 *
	 * @parameter property="project.build.finalName"
	 * @required
	 */
	private String finalName;

	/**
	 * The maven project.
	 *
	 * @parameter property="project"
	 * @required
	 * @readonly
	 */
	private MavenProject project;

	/**
	 * The Maven Session
	 *
	 * @required
	 * @readonly
	 * @parameter property="session"
	 */
	private MavenSession mavenSession;

	/**
	 * The archiver to create the cba
	 *
	 * @component role="org.codehaus.plexus.archiver.Archiver" roleHint="zip"
	 * @required
	 */
	private ZipArchiver zipArchiver;

	@SuppressWarnings("unchecked")
	public void execute() throws MojoExecutionException {
		getLog().info("============== Executing CBA Mojo ==============");
		workDirectory = outputDirectory + "/" + finalName;

		if (getLog().isDebugEnabled()) {
			getLog().debug("Work Directory: " + workDirectory);
			getLog().debug("Output Directory: " + outputDirectory);
			getLog().debug("Composite Bundle Manifest File: " + compositeBundleManifestFile);
			getLog().debug("Final Name: " + finalName);
		}

		try {

			zipArchiver.setCompress(true);
			zipArchiver.setForced(true);

			// Add all dependency jars (bundles)
			Set<Artifact> artifacts = project.getDependencyArtifacts();

			if (artifacts == null || artifacts.size() == 0) {
				throw new MojoExecutionException("There are no dependecy artifacts to create the cba");
			}

			for (Artifact artifact : artifacts) {
				String scope = artifact.getScope();
				if (null == scope || Artifact.SCOPE_COMPILE.equals(scope)) {
					if (getLog().isInfoEnabled()) {
						getLog().info("Dependency artifact [" + artifact.getGroupId() + ":" + artifact.getArtifactId()
								+ ":" + artifact.getVersion() + ":" + artifact.getType() + "]");
					}

					if (getLog().isDebugEnabled()) {
						getLog().debug("File location: " + artifact.getFile());
					}

					if (artifact.getFile() == null) {
						throw new MojoExecutionException(artifact.getGroupId() + ":" + artifact.getArtifactId() + ":"
								+ artifact.getVersion() + ":" + artifact.getType() + " could not be resolved");
					}

					zipArchiver.addFile(artifact.getFile(), artifact.getArtifactId() + "-" + artifact.getVersion() + "."
							+ (artifact.getType() == null ? "jar" : artifact.getType()));
				}
			}
			
			File buildDir = new File(workDirectory);

			// copy composite bundle manifest file to build directory
			if (!compositeBundleManifestFile.exists()) {
				throw new MojoExecutionException("CompositeBundle manifest file not available.");
			} else {
				if (getLog().isInfoEnabled()) {
					getLog().info("Using COMPOSITEBUNDLE.MF from: " + compositeBundleManifestFile);
				}

				File metaInfDir = new File(buildDir, "META-INF");

				FileUtils.copyFileToDirectory(compositeBundleManifestFile, metaInfDir);
			}

			// create pom properties file and add to final zip
			if (project.getArtifact().isSnapshot()) {
				project.setVersion(project.getArtifact().getVersion());
			}

			String groupId = project.getGroupId();

			String artifactId = project.getArtifactId();

			zipArchiver.addFile(project.getFile(), "META-INF/maven/" + groupId + "/" + artifactId + "/pom.xml");

			PomPropertiesUtil pomPropertiesUtil = new PomPropertiesUtil();
			File dir = new File(project.getBuild().getDirectory(), "maven-zip-plugin");
			File pomPropertiesFile = new File(dir, "pom.properties");
			pomPropertiesUtil.createPomProperties(mavenSession, project, zipArchiver, null, pomPropertiesFile, true);
			
//			SimpleDateFormat sdf = new SimpleDateFormat("ddMMyyyyHHmmss");

//			File cbaFile = new File(outputDirectory, finalName + "-" + sdf.format(new Date()) + ".cba");
			File cbaFile = new File(outputDirectory, finalName + ".cba");
			zipArchiver.setDestFile(cbaFile);

			if (buildDir.isDirectory()) {
				zipArchiver.addDirectory(buildDir);
			}

			zipArchiver.createArchive();

			project.getArtifact().setFile(cbaFile);
		} catch (IOException e) {
			throw new MojoExecutionException("Error creating cba", e);
		}
	}
}
