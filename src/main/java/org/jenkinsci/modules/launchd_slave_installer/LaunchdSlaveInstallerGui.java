package org.jenkinsci.modules.launchd_slave_installer;

import hudson.Util;
import hudson.remoting.Callable;
import hudson.remoting.Engine;
import hudson.remoting.jnlp.MainDialog;
import hudson.remoting.jnlp.MainMenu;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
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

            new LaunchdSlaveInstaller(instanceId,
                    new URL(engine.getHudsonUrl(),"computer/"+ Util.rawEncode(engine.slaveName)+"/slave-agent.jnlp")) {

                @Override
                protected void reportError(String msg) {
                    JOptionPane.showMessageDialog(null, msg,
                            Messages.LaunchdSlaveInstaller_DisplayName(), ERROR_MESSAGE);
                }
            }.install();

        } catch (Exception t) {// this runs as a JNLP app, so if we let an exception go, we'll never find out why it failed
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            JOptionPane.showMessageDialog(dialog,sw.toString(),"Error", ERROR_MESSAGE);
        }
    }

    private static final long serialVersionUID = 1L;
}
