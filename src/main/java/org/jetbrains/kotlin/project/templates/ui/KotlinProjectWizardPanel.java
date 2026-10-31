package org.jetbrains.kotlin.project.templates.ui;

import java.awt.Component;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;

public class KotlinProjectWizardPanel implements WizardDescriptor.Panel<WizardDescriptor>, WizardDescriptor.FinishablePanel<WizardDescriptor> {

    private final String defaultProjectName;
    private final boolean isGradle;
    private KotlinProjectPanelVisual visual;
    private final List<ChangeListener> listeners = new CopyOnWriteArrayList<>();
    private WizardDescriptor wizard;

    public KotlinProjectWizardPanel(String defaultProjectName) {
        this(defaultProjectName, false);
    }

    public KotlinProjectWizardPanel(String defaultProjectName, boolean isGradle) {
        this.defaultProjectName = defaultProjectName;
        this.isGradle = isGradle;
    }

    @Override
    public Component getComponent() {
        if (visual == null) {
            visual = new KotlinProjectPanelVisual(this, defaultProjectName, isGradle);
        }
        return visual;
    }

    @Override
    public HelpCtx getHelp() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public boolean isValid() {
        return getComponent() != null && visual.valid(wizard);
    }

    @Override
    public boolean isFinishPanel() {
        return true;
    }

    @Override
    public void readSettings(WizardDescriptor settings) {
        this.wizard = settings;
        if (visual != null) {
            visual.read(settings);
        }
    }

    @Override
    public void storeSettings(WizardDescriptor settings) {
        if (visual != null) {
            visual.store(settings);
        }
    }

    @Override
    public void addChangeListener(ChangeListener l) {
        listeners.add(l);
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
        listeners.remove(l);
    }

    public void fireChangeEvent() {
        ChangeEvent ev = new ChangeEvent(this);
        for (ChangeListener l : listeners) {
            l.stateChanged(ev);
        }
    }
}
