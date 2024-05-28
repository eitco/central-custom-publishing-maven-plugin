package de.eitco.cicd.central.publishing;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

@Mojo(name = "create-bundle", defaultPhase = LifecyclePhase.DEPLOY)
public class ZipStagingDirectoryMojo extends AbstractCustomCentralPublishingMojo {

    public static final Path META_DATA_FILE = Path.of("maven-metadata-central-staging.xml");

    @Parameter(defaultValue = "${project.build.directory}/central-staging")
    protected File stagingDirectory;

    @Parameter(defaultValue = "true")
    protected boolean removeMetaDataFiles;

    @Override
    public void execute() throws MojoExecutionException {

        try {

            FileUtils.forceDelete(deployment);

        } catch (IOException e) {

            throw new MojoExecutionException(e);
        }

        try (ZipFile zipFile = new ZipFile(deployment)) {

            if (removeMetaDataFiles) {

                getLog().debug("walking directory " + stagingDirectory.toPath());

                try (Stream<Path> stream = Files.walk(stagingDirectory.toPath())) {

                    List<Path> toDelete = stream
                            .filter(path -> {
                                getLog().debug("encountered path " + path.toFile().getAbsolutePath());

                                return path.toFile().isFile() && path.getFileName().equals(META_DATA_FILE);
                            }).toList();

                    for (Path path : toDelete) {

                        try {

                            getLog().debug("deleting file " + path.toFile().getAbsolutePath());
                            FileUtils.forceDelete(path.toFile());

                        } catch (IOException e) {
                            throw new MojoExecutionException(e);
                        }
                    }
                }

                ZipParameters zipParameters = new ZipParameters();
                zipParameters.setIncludeRootFolder(false);
                zipFile.addFolder(stagingDirectory, zipParameters);
            }

        } catch (IOException e) {

            throw new MojoExecutionException(e);
        }
    }
}
