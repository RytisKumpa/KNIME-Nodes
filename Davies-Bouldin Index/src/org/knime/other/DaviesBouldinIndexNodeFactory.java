package org.knime.other;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;

/**
 * <code>NodeFactory</code> for the "DaviesBouldinIndex" Node.
 * This node computes the Davies-Bouldin index for evaluating clustering performance on a dataset.
 *
 * @author Rytis Kumpa
 */
public class DaviesBouldinIndexNodeFactory 
        extends NodeFactory<DaviesBouldinIndexNodeModel> {

    /**
     * {@inheritDoc}
     */
    @Override
    public DaviesBouldinIndexNodeModel createNodeModel() {
        return new DaviesBouldinIndexNodeModel();
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
    public NodeView<DaviesBouldinIndexNodeModel> createNodeView(final int viewIndex,
            final DaviesBouldinIndexNodeModel nodeModel) {
        return null; //new DaviesBouldinIndexNodeView(nodeModel);
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
        return new DaviesBouldinIndexNodeDialog();
    }

}

