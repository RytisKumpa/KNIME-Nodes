package org.knime.io.other.anonymizer;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentBoolean;
import org.knime.core.node.defaultnodesettings.DialogComponentButtonGroup;
import org.knime.core.node.defaultnodesettings.DialogComponentColumnFilter;
import org.knime.core.node.defaultnodesettings.DialogComponentNumberEdit;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelFilterString;
import org.knime.core.node.defaultnodesettings.SettingsModelInteger;
import org.knime.core.node.defaultnodesettings.SettingsModelString;

/**
 * <code>NodeDialog</code> for the "Anonymizer" Node.
 * This node generates universally unique identifier (UUID) tags or hash codes for each row in selected columns.  
 *
 * This node dialog derives from {@link DefaultNodeSettingsPane} which allows
 * creation of a simple dialog with standard components. If you need a more 
 * complex dialog please derive directly from 
 * {@link org.knime.core.node.NodeDialogPane}.
 * 
 * @author Rytis Kumpa
 */
public class AnonymizerNodeDialog extends DefaultNodeSettingsPane {

    /**
     * New pane for configuring Anonymizer node dialog.
     * This is just a suggestion to demonstrate possible default dialog
     * components.
     */
    protected AnonymizerNodeDialog() {
        super();
        
        // the column filter dialog component
        addDialogComponent(new DialogComponentColumnFilter(
        		new SettingsModelFilterString(AnonymizerNodeModel.CFGKEY_SELECT), 0, Boolean.FALSE));
        
        // the "Append identifier" checkbox dialog component. This button allows the user to select 
        // whether the identifier should be appended or replace the original string.
        final String m_defaultAppendTitle = "Append identifier";
        
        addDialogComponent(new DialogComponentBoolean(
        		new SettingsModelBoolean(AnonymizerNodeModel.CFGKEY_APPEND, Boolean.FALSE), m_defaultAppendTitle));
        
        // settings for the anonymization function selection dialog component.
        final String m_functionButtonTitle = "Please select the prefered anonymization function:"; 
        final Boolean m_vertical = false;
        final String[] m_functions = {"UUID", "MD5", "SHA-256", "SHA-384", "SHA-512"};
        final String[] m_functionActionCommands = {"UUID", "MD5", "SHA-256", "SHA-384", "SHA-512"};
        SettingsModelString functionSetting = new SettingsModelString(AnonymizerNodeModel.CFGKEY_FUNCTIONS,  AnonymizerNodeModel.m_warningMessage);       
        addDialogComponent(new DialogComponentButtonGroup(
        		functionSetting, m_functionButtonTitle, m_vertical, m_functions, m_functionActionCommands));
                    
        final String m_maxValuesTitle = "Maximum number of unique UUID values:";
        SettingsModelInteger maxValuesSetting = new SettingsModelInteger(AnonymizerNodeModel.CFGKEY_MAXVALUES, 1000000);
        addDialogComponent(new DialogComponentNumberEdit(maxValuesSetting, m_maxValuesTitle));
  
        functionSetting.addChangeListener(new ChangeListener() {
			
			@Override
			public void stateChanged(ChangeEvent arg0) {
				if(functionSetting.getStringValue() == "UUID") {
					maxValuesSetting.setEnabled(true);
				} else {
					maxValuesSetting.setEnabled(false);
				}
				
			}
		});
        
        
        
    }
}







