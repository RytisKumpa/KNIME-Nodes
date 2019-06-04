package org.knime.io.other.anonymizer;

import org.knime.core.node.NodeView;

/**
 * <code>NodeView</code> for the "Anonymizer" Node.
 * This node generates universally unique identifier (UUID) tags or hash codes for each row in selected columns.  
 *
 * @author Rytis Kumpa
 */
public class AnonymizerNodeView extends NodeView<AnonymizerStreamableNodeModel> {

    /**
     * Creates a new view.
     * 
     * @param nodeModel The model (class: {@link AnonymizerStreamableNodeModel})
     */
    protected AnonymizerNodeView(final AnonymizerStreamableNodeModel nodeModel) {
        super(nodeModel);

        // TODO instantiate the components of the view here.

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void modelChanged() {

        // TODO retrieve the new model from your nodemodel and 
        // update the view.
        AnonymizerStreamableNodeModel nodeModel = 
            (AnonymizerStreamableNodeModel)getNodeModel();
        assert nodeModel != null;
        
        // be aware of a possibly not executed nodeModel! The data you retrieve
        // from your nodemodel could be null, emtpy, or invalid in any kind.
        
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onClose() {
    
        // TODO things to do when closing the view
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onOpen() {

        // TODO things to do when opening the view
    }

}

