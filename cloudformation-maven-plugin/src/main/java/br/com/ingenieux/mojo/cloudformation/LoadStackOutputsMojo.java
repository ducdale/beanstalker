package br.com.ingenieux.mojo.cloudformation;

import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.cloudformation.model.Output;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.apache.commons.lang.StringUtils.isEmpty;

/**
 * Load CloudFormation Stack Output Variables into the Maven Build
 */
@Mojo(name = "load-stack-outputs", defaultPhase = LifecyclePhase.VALIDATE)
public class LoadStackOutputsMojo extends AbstractCloudformationMojo {
    /**
     * If set to true, ignores/skips in case of a missing active stack found
     */
    @Parameter(property="cloudformation.failIfMissing", defaultValue = "false")
    Boolean failIfMissing;

    /**
     * When declaring this, it allows you to properly override a variable
     */
    @Parameter(property="cloudformation.outputMapping")
    Map<String, String> outputMapping = new LinkedHashMap<>();

    public void setOutputMapping(String outputMapping) {
        List<String> nvPairs = Arrays.asList(outputMapping.split(","));

        nvPairs.stream()
                .map(this::extractNVPair)
                .forEach(e -> {
                    if (isEmpty(e.getValue())) {
                        this.outputMapping.remove(e.getKey());
                    } else {
                        this.outputMapping.put(e.getKey(), e.getValue());
                    }
                });
    }

    /**
     * Optional: If Defined, will redirect writing to this file as well.
     */
    @Parameter(property="cloudformation.outputFile")
    File outputFile;

    @Override
    protected Object executeInternal() throws Exception {
        if (shouldFailIfMissingStack(failIfMissing)) {
            return null;
        };

        Map<String, String> result = new LinkedHashMap<>();

        for (Output o : listOutputs()) {
            String propertyName = resolvePropertyName(o);

            getLog().info(" * Found output: " + o);
            getLog().info(" * Setting as:");
            getLog().info("   *   key: " + propertyName);
            getLog().info("   * value: " + o.getOutputValue());

            result.put(propertyName, o.getOutputValue());
        }

        Properties p = null;

        if (null != outputFile) {
            p = new Properties();
        }

        for (Map.Entry<String, String> e : result.entrySet()) {
            session.getSystemProperties().setProperty(e.getKey(), e.getValue());

            if (null != p)
                p.setProperty(e.getKey(), e.getValue());
        }

        if (null != p) {
            p.store(new FileOutputStream(outputFile), "Output from cloudformation-maven-plugin for stackId " + this.stackSummary.getStackId());
        }

        return result;
    }

    private String resolvePropertyName(Output o) {
        // TODO Handle Globs + Eventual Replacements

        if (outputMapping.containsKey(o.getOutputKey())) {
            final String replacementKey = outputMapping.get(o.getOutputKey());

            getLog().info("There's a <outputMapping/> entry for '" +
                    o.getOutputKey() +
                    "' (set to '" + replacementKey +
                    "') declared. Using it instead.");

            return replacementKey;
        }

        return "cloudformation.stack." + o.getOutputKey();
    }

    private Collection<Output> listOutputs() {
        String nextToken = null;
        final DescribeStacksRequest request = new DescribeStacksRequest().withStackName(stackId);
        List<Output> result = new ArrayList<>();

        do {
            request.setNextToken(nextToken);

            final DescribeStacksResult response = getService().describeStacks(request);

            result.addAll(response
                    .getStacks()
                    .stream()
                    .flatMap(stack -> stack.getOutputs().stream())
                    .collect(Collectors.toList()));

            nextToken = response.getNextToken();
        } while (null != nextToken);

        return result;
    }
}