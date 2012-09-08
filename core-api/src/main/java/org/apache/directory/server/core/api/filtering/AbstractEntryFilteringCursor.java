/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.directory.server.core.api.filtering;


import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.directory.server.core.api.interceptor.context.SearchingOperationContext;
import org.apache.directory.server.core.api.txn.TxnHandle;
import org.apache.directory.server.core.api.txn.TxnManager;
import org.apache.directory.shared.ldap.model.exception.OperationAbandonedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public abstract class AbstractEntryFilteringCursor implements EntryFilteringCursor
{
    /** A logger for ths class */
    private static final Logger LOG = LoggerFactory.getLogger( AbstractEntryFilteringCursor.class );

    /** the parameters associated with the search operation */
    protected final SearchingOperationContext searchContext;

    /** The associated TxnManager */
    protected TxnManager txnManager;

    /** The associated transaction */
    protected TxnHandle transaction;

    /** 
     * Entry filtering cursor lock..any access to the cursor is through this lock
     * The lock is reentrant as same thread may lock it several times without unlocking.
     */
    protected ReentrantLock lock = new ReentrantLock();

    /** flag to detect the closed cursor */
    protected boolean closed;

    /** creation timestamp of the cursor */
    protected long timestamp;


    /**
     * An instance for this class
     * @param searchContext The associated search context
     */
    protected AbstractEntryFilteringCursor( SearchingOperationContext searchContext )
    {
        this.searchContext = searchContext;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void setTxnManager( TxnManager txnManager )
    {
        this.txnManager = txnManager;
        transaction = txnManager.getCurTxn();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public TxnHandle getTransaction()
    {
        return transaction;
    }


    /**
     * {@inheritDoc}
     */
    public SearchingOperationContext getOperationContext()
    {
        return searchContext;
    }


    /**
     * {@inheritDoc}
     */
    public boolean isAbandoned()
    {
        return searchContext.isAbandoned();
    }


    /**
     * {@inheritDoc}
     */
    public void setAbandoned( boolean abandoned )
    {
        searchContext.setAbandoned( abandoned );

        if ( abandoned )
        {
            LOG.info( "Cursor has been abandoned." );
        }
    }


    /**
     * {@inheritDoc}
     */
    public void pinCursor()
    {
        lock.lock();

        if ( transaction != null )
        {
            TxnHandle curTxn = txnManager.getCurTxn();

            if ( curTxn != null )
            {
                if ( curTxn != transaction )
                {
                    throw new IllegalStateException( "Shouldnt Have another txn running if cursor has a txn " );
                }
            }
            else
            {
                txnManager.setCurTxn( transaction );
            }
        }
    }


    /**
     * {@inheritDoc}
     */
    public void unpinCursor()
    {
        boolean checkForTxnUnset = false;

        if ( txnManager != null )
        {
            checkForTxnUnset = ( txnManager.getCurTxn() == transaction );
        }
        lock.unlock();

        if ( checkForTxnUnset && !lock.isHeldByCurrentThread() )
        {
            txnManager.setCurTxn( null );
        }
    }


    /**
     * {@inheritDoc}
     */
    public void setTimestamp( long timestamp )
    {
        this.timestamp = timestamp;
    }


    /**
     * {@inheritDoc}
     */
    public long getTimestamp()
    {
        return timestamp;
    }


    protected void endTxnAtClose( boolean abort ) throws Exception
    {
        if ( transaction == null )
        {
            return;
        }

        if ( transaction.getState() == TxnHandle.State.COMMIT ||
            transaction.getState() == TxnHandle.State.ABORT )
        {
            return;
        }

        // If this thread already owns the txn, then end it and return
        TxnHandle curTxn = txnManager.getCurTxn();

        if ( curTxn != transaction )
        {
            throw new IllegalStateException( "Shouldnt Have another txn running if cursor has a txn " );
        }

        if ( abort == false )
        {
            txnManager.commitTransaction();
        }
        else
        {
            txnManager.abortTransaction();
        }
    }
}