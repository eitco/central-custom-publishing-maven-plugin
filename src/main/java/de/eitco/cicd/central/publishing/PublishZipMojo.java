package de.eitco.cicd.central.publishing;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Server;
import org.sonatype.central.publisher.client.PublisherClient;
import org.sonatype.central.publisher.client.model.PublishingType;
import org.sonatype.central.publisher.plugin.Constants;
import org.sonatype.central.publisher.plugin.config.PlexusContextConfig;
import org.sonatype.central.publisher.plugin.model.UploadArtifactRequest;
import org.sonatype.central.publisher.plugin.model.WaitForDeploymentStateRequest;
import org.sonatype.central.publisher.plugin.model.WaitUntilRequest;
import org.sonatype.central.publisher.plugin.uploader.ArtifactUploader;
import org.sonatype.central.publisher.plugin.utils.AuthData;
import org.sonatype.central.publisher.plugin.watcher.DeploymentPublishedWatcher;

import java.util.Objects;

import static org.sonatype.central.publisher.client.PublisherConstants.DEFAULT_ORGANIZATION_ID;
import static org.sonatype.central.publisher.client.httpclient.auth.AuthProviderType.BASIC;
import static org.sonatype.central.publisher.client.httpclient.auth.AuthProviderType.USERTOKEN;
import static org.sonatype.central.publisher.plugin.Constants.*;

@Mojo(name = "publish", defaultPhase = LifecyclePhase.DEPLOY)
public class PublishZipMojo extends AbstractCustomCentralPublishingMojo {

    @Parameter(defaultValue = PUBLISHING_SERVER_ID_DEFAULT_VALUE)
    private String publishingServerId;

    @Parameter(defaultValue = CENTRAL_BASE_URL_DEFAULT_VALUE)
    private String centralBaseUrl;

    @Parameter(defaultValue = AUTO_PUBLISH_DEFAULT_VALUE)
    private boolean autoPublish;

    @Parameter(defaultValue = "${project.groupId}:${project.artifactId}:${project.version}")
    private String deploymentName;

    @Parameter(defaultValue = "true")
    private boolean tokenAuth;

    @Parameter(defaultValue = "PUBLISHED")
    private String waitUntil;

    @Parameter(defaultValue = WAIT_MAX_TIME_DEFAULT_VALUE)
    private int waitMaxTime;

    /**
     * Assign the amount of seconds between checking whether a deployment has published. Can not be less than
     * {@link Constants#WAIT_POLLING_INTERVAL_DEFAULT_VALUE}.
     */
    @Parameter(defaultValue = WAIT_POLLING_INTERVAL_DEFAULT_VALUE)
    private int waitPollingInterval;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession mavenSession;

    @Component
    private PlexusContextConfig plexusContextConfig;

    @Component
    private ArtifactUploader artifactUploader;

    @Component
    private DeploymentPublishedWatcher deploymentPublishedWatcher;

    @Component
    private PublisherClient publisherClient;

    private WaitUntilRequest waitUntilRequest;

    @Override
    public void execute() {

        publisherClient.setCentralBaseUrl(centralBaseUrl);

        Server server = mavenSession.getSettings().getServer(publishingServerId);

        if (tokenAuth) {

            publisherClient.setAuthProvider(USERTOKEN, DEFAULT_ORGANIZATION_ID, server.getUsername(), server.getPassword());

        } else {

            publisherClient.setAuthProvider(BASIC, DEFAULT_ORGANIZATION_ID, server.getUsername(), server.getPassword());
        }

        getLog().info("uploading " + deployment.getAbsolutePath());

        PublishingType publishingType = autoPublish ? PublishingType.AUTOMATIC : PublishingType.USER_MANAGED;

        UploadArtifactRequest uploadRequest = new UploadArtifactRequest(deploymentName, deployment.toPath(), publishingType);
        String deploymentId = artifactUploader.upload(uploadRequest);

        getLog().info("deployed to central with deployment id " + deploymentId);

        if (getWaitUntil() == WaitUntilRequest.UPLOADED) {

            return;
        }

        getLog().info("waiting for deployment state to be " + getWaitUntil());

        WaitForDeploymentStateRequest waitForDeploymentStateRequest = new WaitForDeploymentStateRequest(
                centralBaseUrl,
                deploymentId,
                waitUntilRequest,
                waitMaxTime,
                waitPollingInterval
        );

        deploymentPublishedWatcher.waitForDeploymentState(waitForDeploymentStateRequest);
    }

    public WaitUntilRequest getWaitUntil() {

        return Objects.requireNonNullElseGet(waitUntilRequest,
                () -> {

                    waitUntilRequest = WaitUntilRequest.valueOf(waitUntil.toUpperCase());

                    if (waitUntilRequest == WaitUntilRequest.PUBLISHED && !autoPublish) {

                        throw new RuntimeException("cannot wait until published, when autopublish is disabled");
                    }

                    return waitUntilRequest;
                }
        );

    }
}
