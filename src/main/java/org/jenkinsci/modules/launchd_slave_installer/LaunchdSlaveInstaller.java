package org.jenkinsci.modules.launchd_slave_installer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.FilePath;
import hudson.util.ArgumentListBuilder;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.modules.slave_installer.AbstractUnixSlaveInstaller;
import org.jenkinsci.modules.slave_installer.InstallationException;
import org.jenkinsci.modules.slave_installer.LaunchConfiguration;
import org.jenkinsci.modules.slave_installer.Prompter;
import org.jvnet.localizer.Localizable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Performs the actual slave installation.
 *
 * @author Kohsuke Kawaguchi
 */
public class LaunchdSlaveInstaller extends AbstractUnixSlaveInstaller {

    private static final long serialVersionUID = 1;

    private final String instanceId;
    private transient File tmpDir;

    public LaunchdSlaveInstaller(String instanceId) {
        this.instanceId = instanceId;
    }

    @Override
    public Localizable getConfirmationText() {
        return Messages._LaunchdSlaveInstaller_ConfirmationText();
    }

    @SuppressFBWarnings("DM_EXIT")
    @Override
    public void install(LaunchConfiguration params, Prompter prompter) throws InstallationException, IOException, InterruptedException {
        tmpDir = File.createTempFile("jenkins", "tmp");
        if (!tmpDir.delete()) {
            throw new IOException();
        }
        if (!tmpDir.mkdirs()) {
            throw new IOException();
        }

        File sudo = copyResourceIntoExecutableFile("cocoasudo");
        File installSh = copyResourceIntoExecutableFile("install.sh");

        File slaveJar = params.getJarFile();

        String plist = IOUtils.toString(getClass().getResourceAsStream("jenkins-slave.plist"));
        plist = plist
                .replace("{username}", getCurrentUnixUserName())
                .replace("{instanceId}", instanceId)
                .replace("{args}", toArgStrings(params.buildRunnerArguments()));

        File plistFile = File.createTempFile("jenkins-slave","plist");
        FileUtils.writeStringToFile(plistFile, plist);


        ArgumentListBuilder args = new ArgumentListBuilder()
                .add(sudo)
                // .add("--icon=/path/to/icon.icns")
                .add("--prompt=Jenkins requires your password to register a slave agent as a start-up service")
                .add(installSh)
                .add(plistFile)
                .add(slaveJar)
                .add(instanceId);
        final String[] cmds = args.toCommandArray();

        // let the installation start after we close our connection, to avoid conflicts
        // because this code runs after the channel gets closed, we shouldn't rely on any extra libraries
        Runtime.getRuntime().addShutdownHook(new Thread("service starter") {
            @Override
            public void run() {
                try {
                    Process p = new ProcessBuilder(cmds).redirectErrorStream(true).start();
                    p.getOutputStream().close();
                    consume(p.getInputStream());
                    int r = p.waitFor();
                    if (r!=0) // error, but too late to recover
                        reportError("Failed to install as a service: " + r);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            private void consume(InputStream in) throws IOException {
                byte[] buf = new byte[1024];
                while (in.read(buf) >= 0) {/* proceed */}
            }
        });
        System.exit(0);
    }

    private String toArgStrings(ArgumentListBuilder args) {
        StringBuilder buf = new StringBuilder();
        for (String s : args.toList()) {
            buf.append("      <string>").append(s).append("</string>\n");
        }
        return buf.toString();
    }

    private File copyResourceIntoExecutableFile(String resourceName) throws IOException, InterruptedException {
        File f = new File(tmpDir,resourceName);
        FileUtils.copyURLToFile(getClass().getResource(resourceName), f);
        new FilePath(f).chmod(0755);
        return f;
    }
}
