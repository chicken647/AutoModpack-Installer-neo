package net.minecraftforge.installer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraftforge.installer.automodpack.Installation;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

public final class WrapperInstaller {
    private static final Logger LOGGER = Logger.getLogger(WrapperInstaller.class.getName());

    private WrapperInstaller() {
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            runCommandLine(args);
        } else {
            javax.swing.SwingUtilities.invokeLater(WrapperInstaller::showWindow);
        }
    }

    private static void runCommandLine(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java -jar installer.jar <version> <action> <directory>");
            System.err.println("Actions: --installClient, --installServer, --fat-installer");
            return;
        }
        try {
            String action = validateAction(args[0]);
            String version = validateVersion(args[1]);
            File target = validateTarget(args[2]);
            File installer = OfficialInstallerLauncher.download(version);
            int exitCode = runInstallation(installer, action, version, target);
            if (exitCode != 0) {
                System.exit(exitCode);
            }
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Command-line installation failed", exception);
            System.exit(1);
        }
    }

    private static String validateAction(String action) {
        if ("--installClient".equals(action) || "--installServer".equals(action) || "--fat-installer".equals(action)) {
            return action;
        }
        throw new IllegalArgumentException("Unsupported installer action");
    }

    private static String validateVersion(String version) {
        NeoForgeVersionApi.installerUrl(version);
        return version;
    }

    private static File validateTarget(String target) {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("Installation directory must not be blank");
        }
        return Path.of(target).toAbsolutePath().normalize().toFile();
    }

    private static int runInstallation(File installer, String action, String neoForgeVersion, File target)
            throws java.io.IOException, InterruptedException {
        return runInstallation(installer, action, neoForgeVersion, target, System.out::println);
    }

    private static int runInstallation(File installer, String action, String neoForgeVersion, File target,
                                       Consumer<String> output) throws java.io.IOException, InterruptedException {
        int exitCode = OfficialInstallerLauncher.run(installer, action, target, output);
        if (exitCode == 0 && installsGame(action)) {
            output.accept("NeoForge installation finished. Checking for AutoModpack... ");
            Installation.installAutomodpack(minecraftVersion(neoForgeVersion), target.toPath(), output);
        }
        return exitCode;
    }

    static boolean installsGame(String action) {
        return "--installClient".equals(action) || "--installServer".equals(action);
    }

    static String minecraftVersion(String neoForgeVersion) {
        int separator = neoForgeVersion.indexOf('-');
        String minecraftVersion = separator >= 0 ? neoForgeVersion.substring(0, separator) : neoForgeVersion;
        String[] parts = minecraftVersion.split("\\.");
        if (separator >= 0 || parts.length <= 2) {
            return minecraftVersion;
        }
        return parts[0] + "." + parts[1];
    }

    private static void showWindow() {
        JFrame frame = new JFrame("NeoForge Installer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);

        JComboBox<String> versions = new JComboBox<>();
        versions.addItem("Loading NeoForge versions...");
        JRadioButton client = new JRadioButton("Client", true);
        JRadioButton server = new JRadioButton("Server");
        JRadioButton fat = new JRadioButton("Fat installer");
        ButtonGroup modes = new ButtonGroup();
        modes.add(client);
        modes.add(server);
        modes.add(fat);
        JTextArea status = new JTextArea("Loading versions from the official NeoForge Maven repository...");
        status.setEditable(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        JButton browse = new JButton("Choose directory");
        JButton install = new JButton("Download and install");
        JLabel completion = new JLabel("Ready");
        completion.setForeground(new java.awt.Color(90, 90, 90));
        final File[] target = {new File(System.getProperty("user.dir"))};
        JTextField directory = new JTextField(target[0].getAbsolutePath());
        directory.setEditable(false);
        directory.setCaretPosition(0);

        browse.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser(target[0]);
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                target[0] = chooser.getSelectedFile();
                directory.setText(target[0].getAbsolutePath());
                directory.setCaretPosition(0);
            }
        });
        install.addActionListener(event -> {
            String version = (String) versions.getSelectedItem();
            if (version == null || version.startsWith("Loading")) {
                return;
            }
            String action;
            if (client.isSelected()) {
                action = "--installClient";
            } else if (server.isSelected()) {
                action = "--installServer";
            } else {
                action = "--fat-installer";
            }
            install.setEnabled(false);
            completion.setText("Installing...");
            completion.setForeground(new java.awt.Color(180, 110, 0));
            status.setText("Downloading official NeoForge " + version + " installer...\n");
            new SwingWorker<Integer, String>() {
                @Override
                protected Integer doInBackground() throws Exception {
                    File installerJar = OfficialInstallerLauncher.download(version);
                    publish("Downloaded official installer: " + installerJar + "\nStarting installation...\n");
                    File destination = action.equals("--fat-installer")
                            ? new File(target[0], "neoforge-" + version + "-fat.jar")
                            : target[0];
                    publish("Using destination: " + destination.getAbsolutePath() + "\n");
                    publish("Running official NeoForge installer...\n");
                    return runInstallation(installerJar, action, version, destination, line -> publish(line + "\n"));
                }

                @Override
                protected void process(List<String> messages) {
                    messages.forEach(status::append);
                }

                @Override
                protected void done() {
                    install.setEnabled(true);
                    try {
                        int result = get();
                        if (result == 0) {
                            status.append("Installation completed successfully.\n");
                            completion.setText("Installation complete");
                            completion.setForeground(new java.awt.Color(0, 128, 0));
                            install.setText("Install again");
                        } else {
                            status.append("Official installer exited with code " + result + ".\n");
                            completion.setText("Installation failed (exit code " + result + ")");
                            completion.setForeground(java.awt.Color.RED);
                        }
                    } catch (Exception exception) {
                        status.append("Installation failed: " + exception.getMessage() + "\n");
                        completion.setText("Installation failed");
                        completion.setForeground(java.awt.Color.RED);
                        JOptionPane.showMessageDialog(frame, exception.toString(), "Installation failed", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 18, 8, 18));

        JLabel versionLabel = new JLabel("NeoForge version:");
        versionLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        top.add(versionLabel);
        top.add(versions);
        top.add(Box.createVerticalStrut(5));
        JPanel modesPanel = new JPanel();
        modesPanel.setLayout(new BoxLayout(modesPanel, BoxLayout.Y_AXIS));
        modesPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        client.setMargin(new Insets(3, 3, 3, 3));
        server.setMargin(new Insets(3, 3, 3, 3));
        fat.setMargin(new Insets(3, 3, 3, 3));
        client.setIconTextGap(10);
        server.setIconTextGap(10);
        fat.setIconTextGap(10);
        modesPanel.add(client);
        modesPanel.add(server);
        modesPanel.add(fat);
        top.add(modesPanel);
        top.add(Box.createVerticalStrut(8));

        JPanel directoryPanel = new JPanel(new BorderLayout(5, 5));
        directoryPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        directoryPanel.add(directory, BorderLayout.CENTER);
        directoryPanel.add(browse, BorderLayout.EAST);
        top.add(directoryPanel);
        frame.add(top, BorderLayout.NORTH);
        status.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 18, 8, 18));
        frame.add(new JScrollPane(status), BorderLayout.CENTER);
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 18, 12, 18));
        buttonPanel.add(Box.createHorizontalGlue());
        buttonPanel.add(completion);
        buttonPanel.add(Box.createHorizontalStrut(12));
        buttonPanel.add(install);
        frame.add(buttonPanel, BorderLayout.SOUTH);
        frame.setMinimumSize(new Dimension(560, 360));
        frame.pack();
        frame.setVisible(true);

        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return NeoForgeVersionApi.getVersions();
            }

            @Override
            protected void done() {
                try {
                    versions.removeAllItems();
                    for (String version : get()) {
                        versions.addItem(version);
                    }
                    status.setText("Select an action and directory, then download the official installer.\n");
                } catch (Exception exception) {
                    versions.removeAllItems();
                    status.setText("Could not load NeoForge versions: " + exception.getMessage());
                }
            }
        }.execute();
    }

}