package org.jetbrains.kotlin.project.templates.ui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.netbeans.spi.project.ui.support.ProjectChooser;
import org.openide.WizardDescriptor;

public class KotlinProjectPanelVisual extends JPanel implements DocumentListener {

    public static final String PROP_PROJECT_NAME = "projectName";
    public static final String PROP_PROJECT_DIR = "projdir";
    public static final String PROP_GROUP_ID = "groupId";
    public static final String PROP_VERSION = "version";
    public static final String PROP_PACKAGE = "package";
    public static final String PROP_JAVA_VERSION = "javaVersion";
    public static final String PROP_GRADLE_DSL = "gradleDsl";

    private final KotlinProjectWizardPanel panel;
    private final String defaultProjectName;
    private final boolean isGradle;

    private final JTextField txtProjectName = new JTextField();
    private final JTextField txtProjectLocation = new JTextField();
    private final JTextField txtProjectFolder = new JTextField();
    private final JTextField txtGroupId = new JTextField("com.mycompany");
    private final JTextField txtVersion = new JTextField("1.0-SNAPSHOT");
    private final JTextField txtPackage = new JTextField("com.mycompany");
    private final javax.swing.JComboBox<String> cmbJavaVersion = new javax.swing.JComboBox<>(new String[]{"21", "17", "25", "11"});
    private final javax.swing.JComboBox<String> cmbGradleDsl = new javax.swing.JComboBox<>(new String[]{"Groovy", "Kotlin"});
    private final JButton btnBrowse = new JButton("Browse...");

    private boolean updating = false;

    public KotlinProjectPanelVisual(KotlinProjectWizardPanel panel, String defaultProjectName) {
        this(panel, defaultProjectName, false);
    }

    public KotlinProjectPanelVisual(KotlinProjectWizardPanel panel, String defaultProjectName, boolean isGradle) {
        this.panel = panel;
        this.defaultProjectName = defaultProjectName;
        this.isGradle = isGradle;
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());

        txtProjectFolder.setEditable(false);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: Project Name
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0.0;
        add(new JLabel("Project Name:"), c);

        c.gridx = 1;
        c.gridy = 0;
        c.weightx = 1.0;
        c.gridwidth = 2;
        add(txtProjectName, c);

        // Row 1: Project Location
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0.0;
        c.gridwidth = 1;
        add(new JLabel("Project Location:"), c);

        c.gridx = 1;
        c.gridy = 1;
        c.weightx = 1.0;
        add(txtProjectLocation, c);

        c.gridx = 2;
        c.gridy = 1;
        c.weightx = 0.0;
        add(btnBrowse, c);

        // Row 2: Project Folder
        c.gridx = 0;
        c.gridy = 2;
        c.weightx = 0.0;
        add(new JLabel("Project Folder:"), c);

        c.gridx = 1;
        c.gridy = 2;
        c.weightx = 1.0;
        c.gridwidth = 2;
        add(txtProjectFolder, c);

        // Row 3: Group Id
        c.gridx = 0;
        c.gridy = 3;
        c.weightx = 0.0;
        c.gridwidth = 1;
        add(new JLabel("Group Id:"), c);

        c.gridx = 1;
        c.gridy = 3;
        c.weightx = 1.0;
        c.gridwidth = 2;
        add(txtGroupId, c);

        // Row 4: Version
        c.gridx = 0;
        c.gridy = 4;
        c.weightx = 0.0;
        c.gridwidth = 1;
        add(new JLabel("Version:"), c);

        c.gridx = 1;
        c.gridy = 4;
        c.weightx = 1.0;
        c.gridwidth = 2;
        add(txtVersion, c);

        // Row 5: Package
        c.gridx = 0;
        c.gridy = 5;
        c.weightx = 0.0;
        c.gridwidth = 1;
        add(new JLabel("Package:"), c);

        c.gridx = 1;
        c.gridy = 5;
        c.weightx = 1.0;
        c.gridwidth = 2;
        add(txtPackage, c);

        if (isGradle) {
            // Row 6: Java Version
            c.gridx = 0;
            c.gridy = 6;
            c.weightx = 0.0;
            c.gridwidth = 1;
            add(new JLabel("Java Version:"), c);

            c.gridx = 1;
            c.gridy = 6;
            c.weightx = 1.0;
            c.gridwidth = 2;
            add(cmbJavaVersion, c);

            // Row 7: Gradle DSL
            c.gridx = 0;
            c.gridy = 7;
            c.weightx = 0.0;
            c.gridwidth = 1;
            add(new JLabel("Gradle DSL:"), c);

            c.gridx = 1;
            c.gridy = 7;
            c.weightx = 1.0;
            c.gridwidth = 2;
            add(cmbGradleDsl, c);

            cmbJavaVersion.addActionListener(e -> panel.fireChangeEvent());
            cmbGradleDsl.addActionListener(e -> panel.fireChangeEvent());
        }

        // Listeners
        txtProjectName.getDocument().addDocumentListener(this);
        txtProjectLocation.getDocument().addDocumentListener(this);
        txtGroupId.getDocument().addDocumentListener(this);
        txtVersion.getDocument().addDocumentListener(this);
        txtPackage.getDocument().addDocumentListener(this);

        btnBrowse.addActionListener(e -> browseLocation());

        initDefaults();
    }

    private void initDefaults() {
        File defaultLocation = ProjectChooser.getProjectsFolder();
        txtProjectLocation.setText(defaultLocation.getAbsolutePath());

        // Find available project name
        String baseName = defaultProjectName;
        String projectName = baseName;
        int count = 1;
        while (new File(defaultLocation, projectName).exists()) {
            projectName = baseName + count++;
        }
        txtProjectName.setText(projectName);
        updatePaths();
    }

    private void browseLocation() {
        JFileChooser chooser = ProjectChooser.projectChooser();
        File currentLoc = new File(txtProjectLocation.getText().trim());
        if (currentLoc.isDirectory()) {
            chooser.setSelectedFile(currentLoc);
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            txtProjectLocation.setText(selected.getAbsolutePath());
        }
    }

    private void updatePaths() {
        if (updating) return;
        updating = true;
        try {
            String name = txtProjectName.getText().trim();
            String loc = txtProjectLocation.getText().trim();
            if (!name.isEmpty() && !loc.isEmpty()) {
                File folder = new File(loc, name);
                txtProjectFolder.setText(folder.getAbsolutePath());

                String grp = txtGroupId.getText().trim();
                String sanitizedName = name.replaceAll("[^a-zA-Z0-9_]", "").toLowerCase();
                if (!sanitizedName.isEmpty()) {
                    txtPackage.setText(grp.isEmpty() ? sanitizedName : grp + "." + sanitizedName);
                }
            }
        } finally {
            updating = false;
        }
    }

    public boolean valid(WizardDescriptor wizard) {
        String name = txtProjectName.getText().trim();
        if (name.isEmpty()) {
            setErrorMessage(wizard, "Project Name cannot be empty.");
            return false;
        }
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0 ||
            name.indexOf('*') >= 0 || name.indexOf('?') >= 0 || name.indexOf('"') >= 0 ||
            name.indexOf('<') >= 0 || name.indexOf('>') >= 0 || name.indexOf('|') >= 0) {
            setErrorMessage(wizard, "Project Name contains invalid characters.");
            return false;
        }

        String location = txtProjectLocation.getText().trim();
        if (location.isEmpty()) {
            setErrorMessage(wizard, "Project Location cannot be empty.");
            return false;
        }
        File locDir = new File(location);
        if (!locDir.exists() || !locDir.isDirectory()) {
            setErrorMessage(wizard, "Project Location directory does not exist.");
            return false;
        }
        if (!locDir.canWrite()) {
            setErrorMessage(wizard, "Project Location is not writable.");
            return false;
        }

        String folder = txtProjectFolder.getText().trim();
        if (folder.isEmpty()) {
            setErrorMessage(wizard, "Project Folder cannot be empty.");
            return false;
        }
        File projDir = new File(folder);
        if (projDir.exists()) {
            String[] list = projDir.list();
            if (list != null && list.length > 0) {
                setErrorMessage(wizard, "Project folder already exists and is not empty: " + folder);
                return false;
            }
        }

        String groupId = txtGroupId.getText().trim();
        if (groupId.isEmpty() || !isValidPackage(groupId)) {
            setErrorMessage(wizard, "Group Id is not a valid Java identifier sequence.");
            return false;
        }

        String version = txtVersion.getText().trim();
        if (version.isEmpty()) {
            setErrorMessage(wizard, "Version cannot be empty.");
            return false;
        }

        String pkg = txtPackage.getText().trim();
        if (pkg.isEmpty() || !isValidPackage(pkg)) {
            setErrorMessage(wizard, "Package name is not a valid Java package identifier.");
            return false;
        }

        setErrorMessage(wizard, null);
        return true;
    }

    private static boolean isValidPackage(String pkg) {
        String[] parts = pkg.split("\\.", -1);
        for (String p : parts) {
            if (p.isEmpty() || !Character.isJavaIdentifierStart(p.charAt(0))) {
                return false;
            }
            for (int i = 1; i < p.length(); i++) {
                if (!Character.isJavaIdentifierPart(p.charAt(i))) {
                    return false;
                }
            }
        }
        return true;
    }

    private void setErrorMessage(WizardDescriptor wizard, String message) {
        if (wizard != null) {
            wizard.putProperty(WizardDescriptor.PROP_ERROR_MESSAGE, message);
        }
    }

    public void store(WizardDescriptor settings) {
        settings.putProperty(PROP_PROJECT_NAME, txtProjectName.getText().trim());
        settings.putProperty(PROP_PROJECT_DIR, new File(txtProjectFolder.getText().trim()));
        settings.putProperty(PROP_GROUP_ID, txtGroupId.getText().trim());
        settings.putProperty(PROP_VERSION, txtVersion.getText().trim());
        settings.putProperty(PROP_PACKAGE, txtPackage.getText().trim());
        if (isGradle) {
            settings.putProperty(PROP_JAVA_VERSION, String.valueOf(cmbJavaVersion.getSelectedItem()).trim());
            settings.putProperty(PROP_GRADLE_DSL, String.valueOf(cmbGradleDsl.getSelectedItem()).trim());
        }
    }

    public void read(WizardDescriptor settings) {
        // Properties can be restored or left as initialized
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        updatePaths();
        panel.fireChangeEvent();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        updatePaths();
        panel.fireChangeEvent();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        updatePaths();
        panel.fireChangeEvent();
    }
}
