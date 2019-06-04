package org.knime.io.other.anonymizer;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;

/**
 * <code>NodeFactory</code> for the "Anonymizer" Node.
 * This node generates universally unique identifier (UUID) tags or hash codes for each row in selected columns.  
 *
 * @author Rytis Kumpa
 */
public class AnonymizerNodeFactory 
        extends NodeFactory<AnonymizerStreamableNodeModel> {

    /**
     * {@inheritDoc}
     */
    @Override
    public AnonymizerStreamableNodeModel createNodeModel() {
        return new AnonymizerStreamableNodeModel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNrNodeViews() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeView<AnonymizerStreamableNodeModel> createNodeView(final int viewIndex,
            final AnonymizerStreamableNodeModel nodeModel) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasDialog() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeDialogPane createNodeDialogPane() {
        return new AnonymizerNodeDialog();
    }

}

