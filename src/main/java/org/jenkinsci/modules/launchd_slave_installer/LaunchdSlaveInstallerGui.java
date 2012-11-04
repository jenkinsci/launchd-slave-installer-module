package org.jenkinsci.modules.launchd_slave_installer;

import hudson.FilePath;
import hudson.Util;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.Engine;
import hudson.remoting.Which;
import hudson.remoting.jnlp.MainDialog;
import hudson.remoting.jnlp.MainMenu;
import hudson.util.ArgumentListBuilder;
import hudson.util.jna.GNUCLibrary;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jvnet.libpam.impl.CLibrary.passwd;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;

import static javax.swing.JOptionPane.*;

/**
 * Provides a GUI in slave JNLP agent to install it as a launchd service.
 *
 * @author Kohsuke Kawaguchi
 */
public class LaunchdSlaveInstallerGui implements Callable<Void,IOException>, ActionListener {
    private final String instanceId;

    private transient Engine engine;
    private transient MainDialog dialog;
    private transient File tmpDir;

    public LaunchdSlaveInstallerGui(String instanceId) {
        this.instanceId = instanceId;
    }

    /**
     * To be executed on each slave JVM.
     */
    public Void call() {
        if (!new File("/bin/launchctl").exists() || !new File("/System/Library/LaunchDaemons").exists())
            return null;    // this isn't Mac OS X that we understands.

        dialog = MainDialog.get();
        if(dialog==null)     return null;    // can't find the main window. Maybe not running with GUI

        // capture the engine
        engine = Engine.current();

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                MainMenu mainMenu = dialog.getMainMenu();
                JMenu m = mainMenu.getFileMenu();
                JMenuItem menu = new JMenuItem(Messages.LaunchdSlaveInstaller_DisplayName(), KeyEvent.VK_S);
                menu.addActionListener(LaunchdSlaveInstallerGui.this);
                m.add(menu);
                mainMenu.commit();
            }
        });

        return null;
    }

    /**
     * Called when the install menu is selected
     */
    public void actionPerformed(ActionEvent e) {
        try {
            int r = JOptionPane.showConfirmDialog(dialog,
                    "This will install a slave agent as a launchd start-up service,\n"+
                    "so that a Jenkins slave starts automatically when the machine boots.\n"+
                    "This slave agent will exit, and a new one will be started by launchd",
                    Messages.LaunchdSlaveInstaller_DisplayName(), OK_CANCEL_OPTION);
            if(r!=JOptionPane.OK_OPTION)    return;

            tmpDir = File.createTempFile("jenkins","tmp");
            tmpDir.delete();
            tmpDir.mkdirs();

            File sudo = copyResourceIntoExecutableFile("cocoasudo");
            File installSh = copyResourceIntoExecutableFile("install.sh");

            File slaveJar = Which.jarFile(Channel.class);

            URL jnlp = new URL(engine.getHudsonUrl(),"computer/"+ Util.rawEncode(engine.slaveName)+"/slave-agent.jnlp");

            String plist = IOUtils.toString(getClass().getResourceAsStream("jenkins-slave.plist"));
            plist = plist
                    .replace("{username}", getCurrentUnixUserName())
                    .replace("{instanceId}", instanceId)
                    .replace("{url}", jnlp.toExternalForm());

            File plistFile = File.createTempFile("jenkins-slave","plist");
            FileUtils.writeStringToFile(plistFile,plist);


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
        } catch (Exception t) {// this runs as a JNLP app, so if we let an exception go, we'll never find out why it failed
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            JOptionPane.showMessageDialog(dialog,sw.toString(),"Error", ERROR_MESSAGE);
        }
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

    private static final long serialVersionUID = 1L;
}
