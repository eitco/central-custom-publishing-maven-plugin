package de.eitco.cicd.central.publishing;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;

public abstract class AbstractCustomCentralPublishingMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.directory}/central-publishing/central-bundle.zip")
    protected File deployment;

}
