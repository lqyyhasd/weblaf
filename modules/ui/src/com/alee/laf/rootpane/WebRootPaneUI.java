/*
 * This file is part of WebLookAndFeel library.
 *
 * WebLookAndFeel library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * WebLookAndFeel library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with WebLookAndFeel library.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.alee.laf.rootpane;

import com.alee.api.jdk.Function;
import com.alee.extended.behavior.ComponentMoveBehavior;
import com.alee.extended.behavior.ComponentResizeBehavior;
import com.alee.extended.image.WebImage;
import com.alee.laf.WebLookAndFeel;
import com.alee.laf.button.WebButton;
import com.alee.laf.grouping.GroupPane;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.managers.language.LM;
import com.alee.managers.style.*;
import com.alee.painter.DefaultPainter;
import com.alee.painter.Painter;
import com.alee.painter.PainterSupport;
import com.alee.painter.decoration.states.CompassDirection;
import com.alee.utils.*;
import com.alee.utils.swing.DataRunnable;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicRootPaneUI;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * Custom UI for JRootPane component.
 * This UI also includes custom frame and dialog decorations.
 *
 * @author Mikle Garin
 */

public class WebRootPaneUI extends BasicRootPaneUI implements Styleable, ShapeProvider, MarginSupport, PaddingSupport, SwingConstants
{
    /**
     * todo 1. Do not initialize custom decoration elements when it is not needed
     * todo 2. Probably track content pane change and update its style in future
     */

    /**
     * Root pane styling icons.
     */
    public static ImageIcon minimizeIcon = new ImageIcon ( WebRootPaneUI.class.getResource ( "icons/minimize.png" ) );
    public static ImageIcon minimizeActiveIcon = new ImageIcon ( WebRootPaneUI.class.getResource ( "icons/minimize_active.png" ) );
    public static ImageIcon maximizeIcon = new ImageIcon ( WebRootPaneUI.class.getResource ( "icons/maximize.png" ) );
    public static ImageIcon maximizeActiveIcon = new ImageIcon ( WebRootPaneUI.class.getResource ( "icons/maximize_active.png" ) );
    public static ImageIcon restoreIcon = new ImageIcon ( WebRootPaneUI.class.getResource ( "icons/restore.png" ) );
    public static ImageIcon restoreActiveIcon = new ImageIcon ( WebRootPaneUI.class.getResource ( "icons/restore_active.png" ) );
    public static ImageIcon closeIcon = new ImageIcon ( WebRootPaneUI.class.getResource ( "icons/close.png" ) );
    public static ImageIcon closeActiveIcon = new ImageIcon ( WebRootPaneUI.class.getResource ( "icons/close_active.png" ) );

    /**
     * Style settings.
     */
    protected int iconSize;
    protected String emptyTitleText;
    protected boolean displayTitleComponent;
    protected boolean displayWindowButtons;
    protected boolean displayMinimizeButton;
    protected boolean displayMaximizeButton;
    protected boolean displayCloseButton;
    protected boolean displayMenuBar;

    /**
     * Component painter.
     */
    @DefaultPainter ( RootPanePainter.class )
    protected IRootPanePainter painter;

    /**
     * Additional components used be the UI.
     */
    protected JComponent titleComponent;
    protected GroupPane buttonsPanel;
    protected WebButton minimizeButton;
    protected WebButton maximizeButton;
    protected WebButton closeButton;

    /**
     * Runtime variables
     */
    protected Insets margin = null;
    protected Insets padding = null;
    protected JRootPane root;
    protected Window window;
    protected Frame frame;
    protected Dialog dialog;
    protected LayoutManager previousLayoutManager;
    protected LayoutManager layoutManager;
    protected PropertyChangeListener resizableChangeListener;
    protected PropertyChangeListener windowTitleListener;

    /**
     * Returns an instance of the WebRootPaneUI for the specified component.
     * This tricky method is used by UIManager to create component UIs when needed.
     *
     * @param c component that will use UI instance
     * @return instance of the WebRootPaneUI
     */
    @SuppressWarnings ( "UnusedParameters" )
    public static ComponentUI createUI ( final JComponent c )
    {
        return new WebRootPaneUI ();
    }

    @Override
    public void installUI ( final JComponent c )
    {
        super.installUI ( c );

        // Saving root pane reference
        root = ( JRootPane ) c;

        // Applying skin
        StyleManager.installSkin ( root );

        // Installing content pane style ID
        // We are not listening for its changes since the only way is to track layered pane contents
        // In that case we would also need to track root pane contents for layered pane changes which is excessive
        // Content pane is usually not changed or provided by the root pane override and this style will be applied then
        final Container contentPane = root.getContentPane ();
        if ( contentPane instanceof JComponent )
        {
            StyleId.rootpaneContent.at ( root ).set ( ( JComponent ) contentPane );
        }
    }

    @Override
    public void uninstallUI ( final JComponent c )
    {
        // Uninstalling applied skin
        StyleManager.uninstallSkin ( root );

        // Cleaning up runtime variables
        root = null;

        super.uninstallUI ( c );
    }

    @Override
    public StyleId getStyleId ()
    {
        return StyleManager.getStyleId ( root );
    }

    @Override
    public StyleId setStyleId ( final StyleId id )
    {
        return StyleManager.setStyleId ( root, id );
    }

    @Override
    public Shape provideShape ()
    {
        return PainterSupport.getShape ( root, painter );
    }

    @Override
    public Insets getMargin ()
    {
        return margin;
    }

    @Override
    public void setMargin ( final Insets margin )
    {
        this.margin = margin;
        PainterSupport.updateBorder ( getPainter () );
    }

    @Override
    public Insets getPadding ()
    {
        return padding;
    }

    @Override
    public void setPadding ( final Insets padding )
    {
        this.padding = padding;
        PainterSupport.updateBorder ( getPainter () );
    }

    /**
     * Returns root pane painter.
     *
     * @return root pane painter
     */
    public Painter getPainter ()
    {
        return PainterSupport.getAdaptedPainter ( painter );
    }

    /**
     * Sets root pane painter.
     * Pass null to remove root pane painter.
     *
     * @param painter new root pane painter
     */
    public void setPainter ( final Painter painter )
    {
        PainterSupport.setPainter ( root, new DataRunnable<IRootPanePainter> ()
        {
            @Override
            public void run ( final IRootPanePainter newPainter )
            {
                WebRootPaneUI.this.painter = newPainter;
            }
        }, this.painter, painter, IRootPanePainter.class, AdaptiveRootPanePainter.class );
    }

    /**
     * Returns whether or not this root pane uses custom decoration for its window.
     *
     * @return true if this root pane uses custom decoration for its window, false otherwise
     */
    public boolean isDecorated ()
    {
        return painter != null && painter.isDecorated ();
    }

    /**
     * Returns window title component.
     *
     * @return window title component
     */
    public JComponent getTitleComponent ()
    {
        return titleComponent;
    }

    /**
     * Sets window title component.
     *
     * @param title new window title component
     */
    public void setTitleComponent ( final JComponent title )
    {
        this.titleComponent = title;
        root.revalidate ();
    }

    /**
     * Returns window buttons panel.
     *
     * @return window buttons panel
     */
    public GroupPane getButtonsPanel ()
    {
        return buttonsPanel;
    }

    /**
     * Returns whether or not window title component should be displayed.
     *
     * @return true if window title component should be displayed, false otherwise
     */
    public boolean isDisplayTitleComponent ()
    {
        return displayTitleComponent;
    }

    /**
     * Sets whether or not window title component should be displayed.
     *
     * @param display whether or not window title component should be displayed
     */
    public void setDisplayTitleComponent ( final boolean display )
    {
        this.displayTitleComponent = display;
        root.revalidate ();
    }

    /**
     * Returns whether or not window buttons should be displayed.
     *
     * @return true if window buttons should be displayed, false otherwise
     */
    public boolean isDisplayWindowButtons ()
    {
        return displayWindowButtons;
    }

    /**
     * Sets whether or not window buttons should be displayed.
     *
     * @param display whether or not window buttons should be displayed
     */
    public void setDisplayWindowButtons ( final boolean display )
    {
        this.displayWindowButtons = display;
        root.revalidate ();
    }

    /**
     * Returns whether or not window minimize button should be displayed.
     *
     * @return true if window minimize button should be displayed, false otherwise
     */
    public boolean isDisplayMinimizeButton ()
    {
        return displayMinimizeButton;
    }

    /**
     * Sets whether or not window minimize button should be displayed.
     *
     * @param display whether or not window minimize button should be displayed
     */
    public void setDisplayMinimizeButton ( final boolean display )
    {
        this.displayMinimizeButton = display;
        updateButtons ();
        root.revalidate ();
    }

    /**
     * Returns whether or not window maximize button should be displayed.
     *
     * @return true if window maximize button should be displayed, false otherwise
     */
    public boolean isDisplayMaximizeButton ()
    {
        return displayMaximizeButton;
    }

    /**
     * Sets whether or not window maximize button should be displayed.
     *
     * @param display whether or not window maximize button should be displayed
     */
    public void setDisplayMaximizeButton ( final boolean display )
    {
        this.displayMaximizeButton = display;
        updateButtons ();
        root.revalidate ();
    }

    /**
     * Returns whether or not window close button should be displayed.
     *
     * @return true if window close button should be displayed, false otherwise
     */
    public boolean isDisplayCloseButton ()
    {
        return displayCloseButton;
    }

    /**
     * Sets whether or not window close button should be displayed.
     *
     * @param display whether or not window close button should be displayed
     */
    public void setDisplayCloseButton ( final boolean display )
    {
        this.displayCloseButton = display;
        updateButtons ();
        root.revalidate ();
    }

    /**
     * Returns whether or not menu bar should be displayed.
     *
     * @return true if menu bar should be displayed, false otherwise
     */
    public boolean isDisplayMenuBar ()
    {
        return displayMenuBar;
    }

    /**
     * Sets whether or not menu bar should be displayed.
     *
     * @param display whether or not menu bar should be displayed
     */
    public void setDisplayMenuBar ( final boolean display )
    {
        this.displayMenuBar = display;
        root.revalidate ();
    }

    /**
     * Installs window decorations.
     */
    protected void installWindowDecorations ()
    {
        if ( root.getWindowDecorationStyle () != JRootPane.NONE && isDecorated () )
        {
            window = SwingUtils.getWindowAncestor ( root );
            frame = window instanceof Frame ? ( Frame ) window : null;
            dialog = window instanceof Dialog ? ( Dialog ) window : null;
            installSettings ();
            installListeners ();
            installOpacity ();
            installLayout ();
            installDecorationComponents ();
        }
    }

    /**
     * Uninstalls window decorations.
     */
    protected void uninstallWindowDecorations ()
    {
        if ( window != null )
        {
            uninstallDecorationComponents ();
            uninstallLayout ();
            uninstallOpacity ();
            uninstallListeners ();
            uninstallSettings ();
            dialog = null;
            frame = null;
            window = null;
        }
    }

    /**
     * Installs settings used in runtime.
     */
    protected void installSettings ()
    {
        // No settings by default
    }

    /**
     * Uninstalls settings used in runtime.
     */
    protected void uninstallSettings ()
    {
        if ( isFrame () )
        {
            // Resetting maximum frame size
            frame.setMaximizedBounds ( null );
        }
    }

    /**
     * Installs listeners.
     */
    protected void installListeners ()
    {
        // Listen to window resizeability changes
        resizableChangeListener = new PropertyChangeListener ()
        {
            @Override
            public void propertyChange ( final PropertyChangeEvent evt )
            {
                updateButtons ();
            }
        };
        window.addPropertyChangeListener ( WebLookAndFeel.WINDOW_RESIZABLE_PROPERTY, resizableChangeListener );

        // Window resize behavior
        // todo Should this be tied to painter instead?
        ComponentResizeBehavior.install ( root, new Function<Point, CompassDirection> ()
        {
            @Override
            public CompassDirection apply ( final Point p )
            {
                // Ensure dialog or frame is resizable
                if ( dialog != null && dialog.isResizable () || frame != null && frame.isResizable () )
                {
                    // Ensure that point is outside of inner bounds
                    final Rectangle bounds = Bounds.padding.of ( root );
                    final Rectangle inner = GeometryUtils.expand ( bounds, -5, -5, -10, -10 );
                    if ( !inner.contains ( p ) )
                    {
                        // Ensure that point is inside the outer bounds
                        final Rectangle outer = GeometryUtils.expand ( inner, 10, 10, 20, 20 );
                        if ( outer.contains ( p ) )
                        {
                            // Return appropriate resize direction
                            if ( p.y < inner.y )
                            {
                                if ( p.x < inner.x )
                                {
                                    return CompassDirection.northWest;
                                }
                                else if ( p.x > inner.x + inner.width )
                                {
                                    return CompassDirection.northEast;
                                }
                                else
                                {
                                    return CompassDirection.north;
                                }
                            }
                            else if ( p.y > inner.y + inner.height )
                            {
                                if ( p.x < inner.x )
                                {
                                    return CompassDirection.southWest;
                                }
                                else if ( p.x > inner.x + inner.width )
                                {
                                    return CompassDirection.southEast;
                                }
                                else
                                {
                                    return CompassDirection.south;
                                }
                            }
                            else if ( p.x < inner.x )
                            {
                                return CompassDirection.west;
                            }
                            else if ( p.x > inner.x + inner.width )
                            {
                                return CompassDirection.east;
                            }
                        }
                    }
                }
                return null;
            }
        } );
    }

    /**
     * Uninstalls listeners.
     */
    protected void uninstallListeners ()
    {
        ComponentResizeBehavior.uninstall ( root );
        window.removePropertyChangeListener ( WebLookAndFeel.WINDOW_RESIZABLE_PROPERTY, resizableChangeListener );
    }

    /**
     * Installs window opacity.
     */
    protected void installOpacity ()
    {
        if ( ProprietaryUtils.isWindowTransparencyAllowed () )
        {
            root.setOpaque ( false );
            ProprietaryUtils.setWindowOpaque ( window, false );
        }
    }

    /**
     * Uninstalls window opacity.
     */
    protected void uninstallOpacity ()
    {
        if ( ProprietaryUtils.isWindowTransparencyAllowed () )
        {
            root.setOpaque ( true );
            ProprietaryUtils.setWindowOpaque ( window, true );
        }
    }

    /**
     * Installs appropriate layout manager.
     */
    protected void installLayout ()
    {
        if ( layoutManager == null )
        {
            layoutManager = new WebRootPaneLayout ();
        }
        previousLayoutManager = root.getLayout ();
        root.setLayout ( layoutManager );
    }

    /**
     * Uninstalls layout manager.
     */
    protected void uninstallLayout ()
    {
        if ( previousLayoutManager != null )
        {
            root.setLayout ( previousLayoutManager );
            previousLayoutManager = null;
            layoutManager = null;
        }
    }

    /**
     * Installs decoration components.
     */
    protected void installDecorationComponents ()
    {
        // Title
        createTitleComponent ();

        // Buttons
        updateButtons ();
    }

    /**
     * Uninstalls decoration components.
     */
    protected void uninstallDecorationComponents ()
    {
        // Title
        destroyTitleComponent ();

        // Buttons
        if ( buttonsPanel != null )
        {
            root.remove ( buttonsPanel );
            buttonsPanel = null;
            minimizeButton = null;
            maximizeButton = null;
            closeButton = null;
        }
    }

    /**
     * Creates window title component.
     */
    protected void createTitleComponent ()
    {
        // Title panel
        final StyleId titlePanelId = StyleId.rootpaneTitlePanel.at ( root );
        final WebPanel titlePanel = new WebPanel ( titlePanelId, new BorderLayout ( 0, 0 ) );

        // Window icon
        final WebImage titleIcon = new WebImage ( StyleId.rootpaneTitleIcon.at ( titlePanel ), getWindowImage () );
        titlePanel.add ( titleIcon, BorderLayout.LINE_START );

        // Window title
        final WebLabel titleLabel = new WebLabel ( StyleId.rootpaneTitleLabel.at ( titlePanel ), getWindowTitle () );
        titleLabel.setFont ( WebLookAndFeel.globalTitleFont );
        titleLabel.setFontSize ( 13 );
        titleLabel.addComponentListener ( new ComponentAdapter ()
        {
            /**
             * Saving initial alignment to avoid overwriting provided by the style.
             */
            private final int initialAlignment = titleLabel.getHorizontalAlignment ();

            @Override
            public void componentResized ( final ComponentEvent e )
            {
                // Changing title horizontal alignment to avoid title jumping left/right
                final boolean trimmed = titleLabel.getOriginalPreferredSize ().width > titleLabel.getWidth ();
                final boolean ltr = titleLabel.getComponentOrientation ().isLeftToRight ();
                final int alignment = trimmed ? ltr ? LEADING : TRAILING : initialAlignment;
                titleLabel.setHorizontalAlignment ( alignment );
            }
        } );
        titlePanel.add ( titleLabel, BorderLayout.CENTER );

        // Listen to window icon and title changes
        windowTitleListener = new PropertyChangeListener ()
        {
            @Override
            public void propertyChange ( final PropertyChangeEvent evt )
            {
                final String property = evt.getPropertyName ();
                if ( CompareUtils.equals ( property, WebLookAndFeel.WINDOW_ICON_PROPERTY ) )
                {
                    titleIcon.setImage ( getWindowImage () );
                }
                else if ( CompareUtils.equals ( property, WebLookAndFeel.WINDOW_TITLE_PROPERTY ) )
                {
                    titleLabel.setText ( getWindowTitle () );
                }
            }
        };
        window.addPropertyChangeListener ( windowTitleListener );

        // Title move and max/restore actions listener
        final ComponentMoveBehavior cma = new ComponentMoveBehavior ()
        {
            @Override
            public void mouseClicked ( final MouseEvent e )
            {
                if ( isFrame () && isDisplayMaximizeButton () && SwingUtils.isLeftMouseButton ( e ) && e.getClickCount () == 2 )
                {
                    if ( isMaximized () )
                    {
                        restore ();
                    }
                    else
                    {
                        maximize ();
                    }
                }
            }

            @Override
            public void mouseDragged ( final MouseEvent e )
            {
                if ( dragging && isMaximized () )
                {
                    // todo Adjust to mouse location properly
                    // initialPoint = new Point ( initialPoint.x + shadeWidth, initialPoint.y + shadeWidth );
                    restore ();
                }
                super.mouseDragged ( e );
            }
        };
        titlePanel.addMouseListener ( cma );
        titlePanel.addMouseMotionListener ( cma );

        titleComponent = titlePanel;
        root.add ( titleComponent );
    }

    /**
     * Destroys window title component.
     */
    protected void destroyTitleComponent ()
    {
        if ( titleComponent != null )
        {
            window.removePropertyChangeListener ( windowTitleListener );
            root.remove ( titleComponent );
            titleComponent = null;
        }
    }

    /**
     * Updates displayed buttons.
     *
     * todo 1. Optimize button updates
     */
    protected void updateButtons ()
    {
        // Ignore if not decorated
        if ( !isDecorated () )
        {
            return;
        }

        // Creating new buttons panel
        if ( buttonsPanel == null )
        {
            buttonsPanel = new GroupPane ( StyleId.rootpaneButtonsPanel.at ( root ) );
            buttonsPanel.setPaintSides ( false, true, true, true );
            root.add ( buttonsPanel );
        }

        // Minimize button
        if ( displayMinimizeButton && isFrame () )
        {
            if ( minimizeButton == null )
            {
                final StyleId minimizeId = StyleId.rootpaneMinimizeButton.at ( buttonsPanel );
                minimizeButton = new WebButton ( minimizeId, minimizeIcon, minimizeActiveIcon );
                minimizeButton.setName ( "minimize" );
                minimizeButton.addActionListener ( new ActionListener ()
                {
                    @Override
                    public void actionPerformed ( final ActionEvent e )
                    {
                        iconify ();
                    }
                } );
            }
            buttonsPanel.add ( minimizeButton );
        }
        else
        {
            if ( minimizeButton != null )
            {
                buttonsPanel.remove ( minimizeButton );
            }
        }

        // Maximize button
        if ( displayMaximizeButton && isResizable () && isFrame () )
        {
            if ( maximizeButton == null )
            {
                final StyleId maximizeId = StyleId.rootpaneMaximizeButton.at ( buttonsPanel );
                maximizeButton = new WebButton ( maximizeId, maximizeIcon, maximizeActiveIcon )
                {
                    @Override
                    public Icon getIcon ()
                    {
                        return isMaximized () ? restoreIcon : maximizeIcon;
                    }

                    @Override
                    public Icon getRolloverIcon ()
                    {
                        return isMaximized () ? restoreActiveIcon : maximizeActiveIcon;
                    }
                };
                maximizeButton.setName ( "maximize" );
                maximizeButton.addActionListener ( new ActionListener ()
                {
                    @Override
                    public void actionPerformed ( final ActionEvent e )
                    {
                        if ( isFrame () )
                        {
                            if ( isMaximized () )
                            {
                                restore ();
                            }
                            else
                            {
                                maximize ();
                            }
                        }
                    }
                } );
            }
            buttonsPanel.add ( maximizeButton );
        }
        else
        {
            if ( maximizeButton != null )
            {
                buttonsPanel.remove ( maximizeButton );
            }
        }

        // Close button
        if ( displayCloseButton )
        {
            if ( closeButton == null )
            {
                final StyleId closeId = StyleId.rootpaneCloseButton.at ( buttonsPanel );
                closeButton = new WebButton ( closeId, closeIcon, closeActiveIcon );
                closeButton.setName ( "close" );
                closeButton.addActionListener ( new ActionListener ()
                {
                    @Override
                    public void actionPerformed ( final ActionEvent e )
                    {
                        close ();
                    }
                } );
            }
            buttonsPanel.add ( closeButton );
        }
        else
        {
            if ( closeButton != null )
            {
                buttonsPanel.remove ( closeButton );
            }
        }
    }

    /**
     * Returns window title text.
     * Single spacing workaround allows window dragging even when title is not set.
     * That is required because otherwise title label would shrink to zero size due to missing content.
     *
     * @return window title text
     */
    protected String getWindowTitle ()
    {
        final String title = isDialog () ? dialog.getTitle () : isFrame () ? frame.getTitle () : null;
        final String t = !TextUtils.isEmpty ( title ) ? title : emptyTitleText != null ? LM.get ( emptyTitleText ) : null;
        return !TextUtils.isEmpty ( t ) ? t : " ";
    }

    /**
     * Returns window image of suitable size if possible.
     * Image size will not be adjusted here, so make sure to do that if its needed elsewhere.
     *
     * @return window image of suitable size if possible
     */
    protected Image getWindowImage ()
    {
        final List<Image> images = window != null ? window.getIconImages () : null;
        if ( !CollectionUtils.isEmpty ( images ) )
        {
            if ( images.size () > 1 )
            {
                int bestIndex = 0;
                int bestDiff = Math.abs ( images.get ( bestIndex ).getWidth ( null ) - iconSize );
                for ( int i = 1; i < images.size (); i++ )
                {
                    if ( bestDiff == 0 )
                    {
                        break;
                    }
                    final int diff = Math.abs ( images.get ( i ).getWidth ( null ) - iconSize );
                    if ( diff < bestDiff )
                    {
                        bestIndex = i;
                        bestDiff = diff;
                    }
                }
                return images.get ( bestIndex );
            }
            else
            {
                return images.get ( 0 );
            }
        }
        else
        {
            return null;
        }
    }

    /**
     * Closes the Window.
     */
    protected void close ()
    {
        if ( window != null )
        {
            window.dispatchEvent ( new WindowEvent ( window, WindowEvent.WINDOW_CLOSING ) );
        }
    }

    /**
     * Iconifies the Frame.
     */
    protected void iconify ()
    {
        if ( frame != null )
        {
            frame.setExtendedState ( Frame.ICONIFIED );
        }
    }

    /**
     * Maximizes the Frame.
     */
    protected void maximize ()
    {
        if ( frame != null )
        {
            // Updating maximized bounds for the frame
            frame.setMaximizedBounds ( SwingUtils.getMaximizedBounds ( frame ) );

            // Forcing window to go into maximized state
            frame.setExtendedState ( Frame.MAXIMIZED_BOTH );
        }
    }

    /**
     * Restores the Frame size.
     */
    protected void restore ()
    {
        if ( frame != null )
        {
            frame.setExtendedState ( Frame.NORMAL );
        }
    }

    /**
     * Returns whether or not window is resizable.
     *
     * @return true if window is resizable, false otherwise
     */
    protected boolean isResizable ()
    {
        return isDialog () ? dialog.isResizable () : isFrame () && frame.isResizable ();
    }

    /**
     * Returns whether or not this root pane is attached to frame.
     *
     * @return true if this root pane is attached to frame, false otherwise
     */
    public boolean isFrame ()
    {
        return frame != null;
    }

    /**
     * Returns whether or not window this root pane is attached to is maximized.
     *
     * @return true if window this root pane is attached to is maximized, false otherwise
     */
    public boolean isIconified ()
    {
        return isFrame () && ( frame.getExtendedState () & Frame.ICONIFIED ) == Frame.ICONIFIED;
    }

    /**
     * Returns whether or not window this root pane is attached to is maximized.
     *
     * @return true if window this root pane is attached to is maximized, false otherwise
     */
    public boolean isMaximized ()
    {
        return isFrame () && ( frame.getExtendedState () & Frame.MAXIMIZED_BOTH ) == Frame.MAXIMIZED_BOTH;
    }

    /**
     * Returns whether or not this root pane is attached to dialog.
     *
     * @return true if this root pane is attached to dialog, false otherwise
     */
    public boolean isDialog ()
    {
        return dialog != null;
    }

    @Override
    public void paint ( final Graphics g, final JComponent c )
    {
        if ( painter != null )
        {
            painter.paint ( ( Graphics2D ) g, Bounds.component.of ( c ), c, this );
        }
    }

    @Override
    public Dimension getPreferredSize ( final JComponent c )
    {
        // return PainterSupport.getPreferredSize ( c, painter );
        return null;
    }
}