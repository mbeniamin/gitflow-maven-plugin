/*
 * Copyright 2014 Aleksandr Mashchenko.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.amashchenko.maven.plugin.gitflow;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.shared.release.versions.DefaultVersionInfo;
import org.apache.maven.shared.release.versions.VersionParseException;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

/**
 * The git flow hotfix start mojo.
 * 
 * @author Aleksandr Mashchenko
 * 
 */
@Mojo(name = "hotfix-start", aggregator = true)
public class GitFlowHotfixStartMojo extends AbstractGitFlowMojo {

    /** {@inheritDoc} */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            // set git flow configuration
            initGitFlowConfig();

            // check uncommitted changes
            checkUncommittedChanges();

            // need to be in master to get correct project version
            // git checkout master
            executeGitCommand("checkout", gitFlowConfig.getProductionBranch());

            String defaultVersion = "1.0.1";

            // get current project version from pom
            final String currentVersion = getCurrentProjectVersion();

            // get default hotfix version
            try {
                final DefaultVersionInfo versionInfo = new DefaultVersionInfo(
                        currentVersion);
                defaultVersion = versionInfo.getNextVersion()
                        .getReleaseVersionString();
            } catch (VersionParseException e) {
                if (getLog().isDebugEnabled()) {
                    getLog().debug(e);
                }
            }

            String version = null;
            try {
                version = prompter.prompt("What is the hotfix version? ["
                        + defaultVersion + "]");
            } catch (PrompterException e) {
                getLog().error(e);
            }

            if (StringUtils.isBlank(version)) {
                version = defaultVersion;
            }

            // git for-each-ref refs/heads/hotfix/...
            final String hotfixBranch = executeGitCommandReturn("for-each-ref",
                    "refs/heads/" + gitFlowConfig.getHotfixBranchPrefix()
                            + version);

            if (StringUtils.isNotBlank(hotfixBranch)) {
                throw new MojoFailureException(
                        "Hotfix branch with that name already exists. Cannot start hotfix.");
            }

            // git checkout -b hotfix/... master
            executeGitCommand("checkout", "-b",
                    gitFlowConfig.getHotfixBranchPrefix() + version,
                    gitFlowConfig.getProductionBranch());

            // mvn versions:set -DnewVersion=... -DgenerateBackupPoms=false
            executeMvnCommand(VERSIONS_MAVEN_PLUGIN_SET_GOAL, "-DnewVersion="
                    + version, "-DgenerateBackupPoms=false");

            // git commit -a -m updating poms for hotfix
            executeGitCommand("commit", "-a", "-m", "updating poms for hotfix");

            if (installProject) {
                // mvn clean install
                executeMvnCommand("clean", "install");
            }
        } catch (CommandLineException e) {
            getLog().error(e);
        }
    }
}