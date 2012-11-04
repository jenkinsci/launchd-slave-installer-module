package org.jenkinsci.modules.launchd_slave_installer;

import hudson.FilePath;
import hudson.remoting.Channel;
import hudson.remoting.Which;
import hudson.util.ArgumentListBuilder;
import hudson.util.jna.GNUCLibrary;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jvnet.libpam.impl.CLibrary.passwd;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import static javax.swing.JOptionPane.*;

/**
 * Performs the actual slave installation.
 * @author Kohsuke Kawaguchi
 */
public class LaunchdSlaveInstaller {
    private final String instanceId;
    private final URL jnlpFileUrl;
    private transient File tmpDir;

    public LaunchdSlaveInstaller(String instanceId, URL jnlpFileUrl) {
        this.instanceId = instanceId;
        this.jnlpFileUrl = jnlpFileUrl;
    }

    public void install() throws IOException, InterruptedException {
        tmpDir = File.createTempFile("jenkins", "tmp");
        tmpDir.delete();
        tmpDir.mkdirs();

        File sudo = copyResourceIntoExecutableFile("cocoasudo");
        File installSh = copyResourceIntoExecutableFile("install.sh");

        File slaveJar = Which.jarFile(Channel.class);

        String plist = IOUtils.toString(getClass().getResourceAsStream("jenkins-slave.plist"));
        plist = plist
                .replace("{username}", getCurrentUnixUserName())
                .replace("{instanceId}", instanceId)
                .replace("{url}", jnlpFileUrl.toExternalForm());

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

        // let the install runstart after we close our connection, to avoid conflicts
        // because this code runs after the channel gets closed, we shouldn't rely on any extra libraries
        Runtime.getRuntime().addShutdownHook(new Thread("service starter") {
            public void run() {
                try {
                    Process p = new ProcessBuilder(cmds).redirectErrorStream(true).start();
                    p.getOutputStream().close();
                    consume(p.getInputStream());
                    int r = p.waitFor();
                    if (r!=0) // error, but too late to recover
                        JOptionPane.showMessageDialog(null, "Failed to install as a service",
                                Messages.LaunchdSlaveInstaller_DisplayName(), ERROR_MESSAGE);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            private void consume(InputStream in) throws IOException {
                byte[] buf = new byte[1024];
                while (in.read(buf)>=0)
                    ;
            }
        });
        System.exit(0);
    }

    private File copyResourceIntoExecutableFile(String resourceName) throws IOException, InterruptedException {
        File f = new File(tmpDir,resourceName);
        FileUtils.copyURLToFile(getClass().getResource(resourceName), f);
        new FilePath(f).chmod(0755);
        return f;
    }

    private String getCurrentUnixUserName() {
        passwd pwd = GNUCLibrary.LIBC.getpwuid(GNUCLibrary.LIBC.geteuid());
        return pwd.pw_name;
    }
}
