/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.runtimes.builder;

import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.cloud.runtimes.builder.TestUtils.TestWorkspaceBuilder;
import com.google.cloud.runtimes.builder.buildsteps.GradleBuildStep;
import com.google.cloud.runtimes.builder.buildsteps.JettyOptionsBuildStep;
import com.google.cloud.runtimes.builder.buildsteps.MavenBuildStep;
import com.google.cloud.runtimes.builder.buildsteps.PrebuiltRuntimeImageBuildStep;
import com.google.cloud.runtimes.builder.buildsteps.ScriptExecutionBuildStep;
import com.google.cloud.runtimes.builder.buildsteps.SourceBuildRuntimeImageBuildStep;
import com.google.cloud.runtimes.builder.buildsteps.base.BuildStep;
import com.google.cloud.runtimes.builder.buildsteps.base.BuildStepException;
import com.google.cloud.runtimes.builder.buildsteps.base.BuildStepFactory;
import com.google.cloud.runtimes.builder.config.AppYamlFinder;
import com.google.cloud.runtimes.builder.config.AppYamlParser;
import com.google.cloud.runtimes.builder.config.YamlParser;
import com.google.cloud.runtimes.builder.config.domain.AppYaml;
import com.google.cloud.runtimes.builder.config.domain.BuildContext;
import com.google.cloud.runtimes.builder.config.domain.BuildContextFactory;
import com.google.cloud.runtimes.builder.config.domain.RuntimeConfig;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

/**
 * Unit tests for {@link BuildPipelineConfigurator}
 */
public class BuildPipelineConfiguratorTest {

  @Mock private BuildStepFactory buildStepFactory;
  @Mock private BuildContextFactory buildContextFactory;
  @Mock private MavenBuildStep mavenBuildStep;
  @Mock private GradleBuildStep gradleBuildStep;
  @Mock private ScriptExecutionBuildStep scriptExecutionBuildStep;
  @Mock private PrebuiltRuntimeImageBuildStep prebuiltRuntimeImageBuildStep;
  @Mock private SourceBuildRuntimeImageBuildStep sourceBuildRuntimeImageBuildStep;
  @Mock private JettyOptionsBuildStep jettyOptionsBuildStep;
  @Mock private AppYamlFinder appYamlFinder;
  @Mock
  private YamlParser<AppYaml> appYamlYamlParser;
  @Spy
  private AppYaml appYaml = new AppYaml();

  private BuildPipelineConfigurator buildPipelineConfigurator;

  private boolean disableSourceBuild;

  @Before
  public void setUp() throws BuildStepException, IOException {
    MockitoAnnotations.initMocks(this);

    when(buildStepFactory.createMavenBuildStep()).thenReturn(mavenBuildStep);
    when(buildStepFactory.createGradleBuildStep()).thenReturn(gradleBuildStep);
    when(buildStepFactory.createScriptExecutionBuildStep(anyString()))
        .thenReturn(scriptExecutionBuildStep);
    when(buildStepFactory.createPrebuiltRuntimeImageBuildStep())
        .thenReturn(prebuiltRuntimeImageBuildStep);
    when(buildStepFactory.createSourceBuildRuntimeImageStep())
        .thenReturn(sourceBuildRuntimeImageBuildStep);
    when(buildStepFactory.createJettyOptionsBuildStep())
        .thenReturn(jettyOptionsBuildStep);

    when(appYamlYamlParser.parse(any())).thenReturn(appYaml);
    when(appYamlYamlParser.getEmpty()).thenReturn(appYaml);

    disableSourceBuild = false;

    // mock the behavior of guice's assisted inject by passing factory method args to the
    // constructor of BuildContext
    when(buildContextFactory.createBuildContext(any(), any()))
        .then(invocation -> new BuildContext(invocation.getArgument(0), invocation.getArgument(1),
            disableSourceBuild));

    buildPipelineConfigurator = initConfigurator();
  }

  private BuildPipelineConfigurator initConfigurator() {
    return new BuildPipelineConfigurator(appYamlYamlParser, appYamlFinder, buildStepFactory,
        buildContextFactory, Collections.emptyMap());
  }

  private void assertBuildStepsCalledWithRuntimeConfig(RuntimeConfig expected,
      BuildStep... buildSteps) throws BuildStepException, IOException {
    for (BuildStep buildStep : buildSteps) {
      ArgumentCaptor<BuildContext> captor = ArgumentCaptor.forClass(BuildContext.class);
      verify(buildStep, times(1)).run(captor.capture());
      assertRuntimeConfigEquals(expected, captor.getValue().getRuntimeConfig());
    }
    verify(appYaml, times(1)).applyOverrideSettings(any());
  }

  private void assertRuntimeConfigEquals(RuntimeConfig expected, RuntimeConfig actual) {
    assertTrue(Objects.equal(expected.getJdk(), actual.getJdk())
        && Objects.equal(expected.getArtifact(), actual.getArtifact())
        && Objects.equal(expected.getServer(), actual.getServer())
        && Objects.equal(expected.getBuildScript(), actual.getBuildScript())
        && Objects.equal(expected.getJettyQuickstart(), actual.getJettyQuickstart()));
  }

  @Test
  public void testPrebuiltArtifact() throws BuildStepException, IOException {
    Path workspace = new TestWorkspaceBuilder()
        .file("foo.war").build()
        .build();

    buildPipelineConfigurator.generateDockerResources(workspace);

    verify(buildStepFactory, times(1)).createPrebuiltRuntimeImageBuildStep();
    verify(buildStepFactory, times(1)).createJettyOptionsBuildStep();
    verifyNoMoreInteractions(buildStepFactory);

    assertBuildStepsCalledWithRuntimeConfig(new RuntimeConfig(), prebuiltRuntimeImageBuildStep,
        jettyOptionsBuildStep);
  }

  @Test
  public void testMavenSourceBuild() throws BuildStepException, IOException {
    Path workspace = new TestWorkspaceBuilder()
        .file("pom.xml").build()
        .build();

    buildPipelineConfigurator.generateDockerResources(workspace);

    verify(buildStepFactory, times(1)).createMavenBuildStep();
    verify(buildStepFactory, times(1)).createSourceBuildRuntimeImageStep();
    verify(buildStepFactory, times(1)).createJettyOptionsBuildStep();
    verifyNoMoreInteractions(buildStepFactory);

    assertBuildStepsCalledWithRuntimeConfig(new RuntimeConfig(), mavenBuildStep,
        sourceBuildRuntimeImageBuildStep, jettyOptionsBuildStep);
  }

  @Test
  public void testGradleSourceBuild() throws BuildStepException, IOException {
    Path workspace = new TestWorkspaceBuilder()
        .file("build.gradle").build()
        .build();

    buildPipelineConfigurator.generateDockerResources(workspace);

    verify(buildStepFactory, times(1)).createGradleBuildStep();
    verify(buildStepFactory, times(1)).createSourceBuildRuntimeImageStep();
    verify(buildStepFactory, times(1)).createJettyOptionsBuildStep();
    verifyNoMoreInteractions(buildStepFactory);

    assertBuildStepsCalledWithRuntimeConfig(new RuntimeConfig(), gradleBuildStep,
        sourceBuildRuntimeImageBuildStep, jettyOptionsBuildStep);
  }

  @Test
  public void testMavenAndGradleSourceBuild() throws BuildStepException, IOException {
    Path workspace = new TestWorkspaceBuilder()
        .file("pom.xml").build()
        .file("build.gradle").build()
        .build();

    buildPipelineConfigurator.generateDockerResources(workspace);

    verify(buildStepFactory, times(1)).createMavenBuildStep();
    verify(buildStepFactory, times(1)).createSourceBuildRuntimeImageStep();
    verify(buildStepFactory, times(1)).createJettyOptionsBuildStep();
    verifyNoMoreInteractions(buildStepFactory);

    assertBuildStepsCalledWithRuntimeConfig(new RuntimeConfig(), mavenBuildStep,
        sourceBuildRuntimeImageBuildStep, jettyOptionsBuildStep);
  }

  @Test
  public void testMavenBuildWithCustomScriptAndOverrides() throws BuildStepException, IOException {
    String customScript = "custom mvn goals";
    Path workspace = new TestWorkspaceBuilder()
        .file("pom.xml").build()
        .file("app.yaml").withContents(
            "runtime_config:\n"
                + "  jdk: openjdk8\n"
            + "  build_script: " + customScript).build()
        .build();

    Path yamlPath = workspace.resolve("app.yaml");
    when(appYamlFinder.findAppYamlFile(workspace))
        .thenReturn(Optional.of(yamlPath));

    // real parsing is needed for this test
    appYaml = spy(new AppYamlParser().parse(yamlPath));
    when(appYamlYamlParser.parse(yamlPath)).thenReturn(appYaml);
    buildPipelineConfigurator = new BuildPipelineConfigurator(appYamlYamlParser, appYamlFinder,
        buildStepFactory,
        buildContextFactory, ImmutableMap.of("jdk", "fakeJdk"));
    buildPipelineConfigurator.generateDockerResources(workspace);

    verify(buildStepFactory, times(1)).createScriptExecutionBuildStep(eq(customScript));
    verify(buildStepFactory, times(1)).createSourceBuildRuntimeImageStep();
    verify(buildStepFactory, times(1)).createJettyOptionsBuildStep();
    verifyNoMoreInteractions(buildStepFactory);

    RuntimeConfig expectedConfig = new RuntimeConfig();
    expectedConfig.setBuildScript(customScript);
    expectedConfig.setJdk("fakeJdk");
    assertBuildStepsCalledWithRuntimeConfig(expectedConfig, scriptExecutionBuildStep,
        sourceBuildRuntimeImageBuildStep, jettyOptionsBuildStep);
  }

  @Test
  public void testPrebuiltArtifactAndMavenBuild() throws BuildStepException, IOException {
    Path workspace = new TestWorkspaceBuilder()
        .file("pom.xml").build()
        .file("foo.war").build()
        .build();

    buildPipelineConfigurator.generateDockerResources(workspace);

    verify(buildStepFactory, times(1)).createMavenBuildStep();
    verify(buildStepFactory, times(1)).createSourceBuildRuntimeImageStep();
    verify(buildStepFactory, times(1)).createJettyOptionsBuildStep();
    verifyNoMoreInteractions(buildStepFactory);

    assertBuildStepsCalledWithRuntimeConfig(new RuntimeConfig(), mavenBuildStep,
        sourceBuildRuntimeImageBuildStep, jettyOptionsBuildStep);
  }

  @Test
  public void testAppYamlIsDockerignored()
      throws IOException, BuildStepException {
    String relativeAppYamlPath = "foo/bar/app.yaml";
    Path workspace = new TestWorkspaceBuilder()
        .file(relativeAppYamlPath).withContents("env: flex").build()
        .file("app.jar").build()
        .build();

    when(appYamlFinder.findAppYamlFile(workspace))
        .thenReturn(Optional.of(workspace.resolve(relativeAppYamlPath)));

    buildPipelineConfigurator.generateDockerResources(workspace);

    List<String> dockerIgnoreLines = Files.readLines(workspace.resolve(".dockerignore").toFile(),
        Charset.defaultCharset());
    assertTrue(dockerIgnoreLines.contains(relativeAppYamlPath));
  }

  @Test
  public void testSourceBuildDisable() throws BuildStepException, IOException {
    Path workspace = new TestWorkspaceBuilder()
        .file("pom.xml").build()
        .file("foo.war").build()
        .build();

    // create the pipeline configurator with source builds disabled
    disableSourceBuild = true;
    buildPipelineConfigurator = initConfigurator();

    buildPipelineConfigurator.generateDockerResources(workspace);

    verify(buildStepFactory, times(1)).createPrebuiltRuntimeImageBuildStep();
    verify(buildStepFactory, times(1)).createJettyOptionsBuildStep();
    verifyNoMoreInteractions(buildStepFactory);

    assertBuildStepsCalledWithRuntimeConfig(new RuntimeConfig(), prebuiltRuntimeImageBuildStep,
        jettyOptionsBuildStep);
  }

}
