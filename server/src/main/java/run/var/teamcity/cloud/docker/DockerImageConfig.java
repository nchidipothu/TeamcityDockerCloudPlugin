package run.var.teamcity.cloud.docker;


import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.clouds.CloudImageParameters;
import jetbrains.buildServer.serverSide.InvalidProperty;
import org.jetbrains.annotations.Nullable;
import run.var.teamcity.cloud.docker.util.DockerCloudUtils;
import run.var.teamcity.cloud.docker.util.Node;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * A {@link DockerImage} configuration.
 */
public class DockerImageConfig {

    private static final Logger LOG = DockerCloudUtils.getLogger(DockerImageConfig.class);

    public static final int DOCKER_IMAGE_SPEC_VERSION = 1;

    private final String profileName;
    private final Node containerSpec;
    private final boolean rmOnExit;
    private final boolean useOfficialTCAgentImage;
    private final int maxInstanceCount;
    private final Integer agentPoolId;

    public DockerImageConfig(@Nonnull String profileName, @Nonnull Node containerSpec, boolean rmOnExit,
                             boolean useOfficialTCAgentImage, int maxInstanceCount, @Nullable Integer agentPoolId) {
        DockerCloudUtils.requireNonNull(profileName, "Profile name cannot be null.");
        DockerCloudUtils.requireNonNull(containerSpec, "Container specification cannot be null.");
        if (maxInstanceCount < 1) {
            throw new IllegalArgumentException("At least 1 instance must be allowed.");
        }
        this.profileName = profileName;
        this.containerSpec = containerSpec;
        this.rmOnExit = rmOnExit;
        this.useOfficialTCAgentImage = useOfficialTCAgentImage;
        this.maxInstanceCount = maxInstanceCount;
        this.agentPoolId = agentPoolId;
    }

    /**
     * Gets this image profile name.
     *
     * @return the profile name
     */
    @Nonnull
    public String getProfileName() {
        return profileName;
    }

    /**
     * Gets the image container specification.
     *
     * @return the container specification
     */
    @Nonnull
    public Node getContainerSpec() {
        return containerSpec;
    }

    /**
     * Rm-on-exit flag. When {@code true}, the container will be discarded as soon as it is stopped and not be reused.
     *
     * @return {@code true} if the container must be discarded when stopped, {@code false} otherwise
     */
    boolean isRmOnExit() {
        return rmOnExit;
    }

    /**
     * Use official TeamCity agent image.
     *
     * @return {@code true} when the official TeamCity agent image should be used.
     */
    public boolean isUseOfficialTCAgentImage() {
        return useOfficialTCAgentImage;
    }

    /**
     * Gets the maximal number of instance associated with this image.
     *
     * @return the maximal number of instance associated with this image
     */
    public int getMaxInstanceCount() {
        return maxInstanceCount;
    }

    /**
     * Gets the agent pool ID associated with this cloud image (if any).
     *
     * @return the agent pool id or {@code null}
     */
    @Nullable
    public Integer getAgentPoolId() {
        return agentPoolId;
    }

    /**
     * Load a list of cloud images from a configuration properties map. The ordering of the images will be the same
     * than the one specified in the underlying JSON definition.
     *
     * @param properties the map of properties
     *
     * @return the loaded list of images
     *
     * @throws NullPointerException             if the properties map is {@code null}
     * @throws DockerCloudClientConfigException if the image configuration is not valid
     */
    @Nonnull
    public static List<DockerImageConfig> processParams(@Nonnull Map<String, String> properties) {
        DockerCloudUtils.requireNonNull(properties, "Properties map cannot be null.");

        List<InvalidProperty> invalidProperties = new ArrayList<>();

        String imagesJSon = properties.get(DockerCloudUtils.IMAGES_PARAM);
        String imageParametersJson = properties.get(CloudImageParameters.SOURCE_IMAGES_JSON);
        Collection<CloudImageParameters> imagesParameters = null;
        if (imageParametersJson != null) {
            try {
                imagesParameters = CloudImageParameters.collectionFromJson(imageParametersJson);
            } catch (Exception e) {
                LOG.error("Failed to parse cloud image parameters.", e);
            }
        }

        Set<String> profileNames = new HashSet<>();

        List<DockerImageConfig> images = null;
        if (imagesJSon != null && !imagesJSon.isEmpty()) {
            try {
                Node imagesNode = Node.parse(imagesJSon);
                images = new ArrayList<>(imagesNode.getArrayValues().size());
                for (Node imageNode : imagesNode.getArrayValues()) {
                    DockerImageConfig imageConfig = DockerImageConfig.fromJSon(imageNode, imagesParameters);
                    boolean duplicateProfileName = !profileNames.add(imageConfig.getProfileName());
                    if (duplicateProfileName) {
                        invalidProperties.add(new InvalidProperty(DockerCloudUtils.IMAGES_PARAM, "Duplicate profile name: " + imageConfig.getProfileName()));
                    } else {
                        images.add(imageConfig);
                    }
                }
            } catch (Exception e) {
                invalidProperties.add(new InvalidProperty(DockerCloudUtils.IMAGES_PARAM, "Cannot parse image data."));
            }
        } else {
            invalidProperties.add(new InvalidProperty(DockerCloudUtils.IMAGES_PARAM, "No image provided."));
        }

        if (!invalidProperties.isEmpty()) {
            throw new DockerCloudClientConfigException(invalidProperties);
        }

        assert images != null;

        return images;
    }

    /**
     * Load an image configuration from a JSON node.
     *
     * @param node the JSON node
     * @param imagesParameters images parameters provided from the Cloud API if any
     *
     * @return the loaded configuration
     *
     * @throws NullPointerException     if {@code node} is {@code null}
     * @throws IllegalArgumentException if no valid configuration could be build from the provided JSON node
     */
    @Nonnull
    public static DockerImageConfig fromJSon(@Nonnull Node node, @Nullable Collection<CloudImageParameters> imagesParameters) {
        DockerCloudUtils.requireNonNull(node, "JSON node cannot be null.");
        try {
            Node admin = node.getObject("Administration");
            if (admin.getAsInt("Version") != DOCKER_IMAGE_SPEC_VERSION) {
                throw new IllegalArgumentException("Unsupported image specification version.");
            }

            Node container = node.getObject("Container");

            Node env = container.getArray("Env", Node.EMPTY_ARRAY);
            for (Node value : env.getArrayValues()) {
                String envValue = value.getAsString();
                if (envValue.startsWith(DockerCloudUtils.ENV_PREFIX)) {
                    throw new IllegalArgumentException("Variable start with reserved prefix: " + envValue);
                }
            }

            Node labels = container.getObject("Labels", Node.EMPTY_OBJECT);
            assert labels != null;
            for (String key : labels.getObjectValues().keySet()) {
                if (key.startsWith(DockerCloudUtils.NS_PREFIX)) {
                    throw new IllegalArgumentException("Label key start with reserved prefix: " + key);
                }
            }

            String profileName = admin.getAsString("Profile");
            boolean deleteOnExit = admin.getAsBoolean("RmOnExit");
            boolean useOfficialTCAgentImage = admin.getAsBoolean("UseOfficialTCAgentImage");

            Integer agentPoolId = null;
            if (imagesParameters != null) {
                for (CloudImageParameters imageParameter : imagesParameters) {
                    if (profileName.equals(imageParameter.getId())) {
                        agentPoolId = imageParameter.getAgentPoolId();
                        break;
                    }
                }
            }

            return new DockerImageConfig(profileName, node.getObject("Container"), deleteOnExit,
                    useOfficialTCAgentImage, admin.getAsInt("MaxInstanceCount", -1), agentPoolId);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse image JSON definition:\n" + node, e);
        }
    }
}
