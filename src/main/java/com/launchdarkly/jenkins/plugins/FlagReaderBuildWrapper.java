package com.launchdarkly.jenkins.plugins;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;
import com.launchdarkly.shaded.com.google.gson.Gson;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildWrapperDescriptor;
import jenkins.tasks.SimpleBuildWrapper;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.io.PrintStream;
import java.time.Duration;

public class FlagReaderBuildWrapper extends SimpleBuildWrapper {
    private String context;
    private String flagKey;
    private String sdkKey;
    private String environmentVariable;

    @DataBoundConstructor
    public FlagReaderBuildWrapper(String sdkKey, String flagKey, String context, String environmentVariable) {
        this.sdkKey = sdkKey;
        this.flagKey = flagKey;
        this.context = context;
        this.environmentVariable = environmentVariable;
    }

    public String getSdkKey() {
        return sdkKey;
    }

    @DataBoundSetter
    public void setSdkKey(String sdkKey) {
        this.sdkKey = sdkKey;
    }

    public String getFlagKey() {
        return this.flagKey;
    }

    @DataBoundSetter
    public void setFlagKey(String flagKey) {
        this.flagKey = flagKey;
    }

    public String getContext() {
        return this.context;
    }

    @DataBoundSetter
    public void setContext(String context) {
        this.context = context;
    }

    public String getEnvironmentVariable() {
        return this.environmentVariable;
    }

    @DataBoundSetter
    public void setEnvironmentVariable(String environmentVariable) {
        this.environmentVariable = environmentVariable;
    }

    private synchronized LDClient makeClient(PrintStream logger) {
        var config =
                new LDConfig.Builder()
                        .startWait(Duration.ofSeconds(30))
                        //.logging(Components.logging(Logs.toStream(logger)).level(LDLogLevel.DEBUG))
                        .build();
        //logger.print("LaunchDarkly Flag Reader: LDClient initialized.");
        return new LDClient(sdkKey, config);
    }

    @Override
    public void setUp(
            Context context,
            Run<?, ?> run,
            FilePath workspace,
            Launcher launcher,
            TaskListener listener,
            EnvVars initialEnvironment)
            throws IOException, InterruptedException {
        var logger = listener.getLogger();
        try (LDClient client = makeClient(logger)) {
            var jsonContext = new Gson().fromJson(getContext(), LDContext.class);
            var flagValue = client.jsonValueVariation(getFlagKey(), jsonContext, null).toString();
            logger.printf("LaunchDarkly Flag Reader: Found feature flag '%s', value = '%s'%n", getFlagKey(), flagValue);
            context.env(getEnvironmentVariable(), flagValue);
            logger.printf(
                    "LaunchDarkly Flag Reader: Stored ENV variable: %s=%s%n",
                    getEnvironmentVariable(), context.getEnv().get(getEnvironmentVariable()));
        } catch (Exception ex) {
            logger.printf("LaunchDarkly Flag Reader: Exception = %s", ex);
        }
    }

    @Extension
    public static final class Descriptor extends BuildWrapperDescriptor {

        @Override
        public boolean configure(StaplerRequest request, JSONObject form) throws FormException {
            return super.configure(request, form);
        }

        @Override
        public boolean isApplicable(final AbstractProject<?, ?> item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.FlagReader_Descriptor_DisplayName();
        }
    }
}
