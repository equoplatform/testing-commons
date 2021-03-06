/****************************************************************************
**
** Copyright (C) 2021 Equo
**
** This file is part of the Equo SDK.
**
** Commercial License Usage
** Licensees holding valid commercial Equo licenses may use this file in
** accordance with the commercial license agreement provided with the
** Software or, alternatively, in accordance with the terms contained in
** a written agreement between you and Equo. For licensing terms
** and conditions see https://www.equo.dev/terms.
**
** GNU General Public License Usage
** Alternatively, this file may be used under the terms of the GNU
** General Public License version 3 as published by the Free Software
** Foundation. Please review the following
** information to ensure the GNU General Public License requirements will
** be met: https://www.gnu.org/licenses/gpl-3.0.html.
**
****************************************************************************/

package com.equo.testing.common.util;

import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.eclipse.core.commands.CommandManager;
import org.eclipse.e4.core.commands.ECommandService;
import org.eclipse.e4.core.commands.EHandlerService;
import org.eclipse.e4.core.commands.internal.CommandServiceImpl;
import org.eclipse.e4.core.commands.internal.HandlerServiceImpl;
import org.eclipse.e4.core.contexts.EclipseContextFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.services.contributions.IContributionFactory;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.e4.core.services.log.Logger;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.e4.ui.internal.workbench.E4Workbench;
import org.eclipse.e4.ui.internal.workbench.ModelServiceImpl;
import org.eclipse.e4.ui.internal.workbench.ReflectionContributionFactory;
import org.eclipse.e4.ui.internal.workbench.addons.CommandProcessingAddon;
import org.eclipse.e4.ui.internal.workbench.addons.HandlerProcessingAddon;
import org.eclipse.e4.ui.internal.workbench.swt.ResourceUtility;
import org.eclipse.e4.ui.model.application.MAddon;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.MApplicationElement;
import org.eclipse.e4.ui.workbench.IResourceUtilities;
import org.eclipse.e4.ui.workbench.IWorkbench;
import org.eclipse.e4.ui.workbench.modeling.EModelService;
import org.eclipse.e4.ui.workbench.renderers.swt.WorkbenchRendererFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.runners.model.Statement;
import org.osgi.framework.FrameworkUtil;

import com.equo.testing.common.statements.RunWithE4ModelStatement;

/**
 * Rule that makes possible the initialization of an application context.
 */
public class EquoRule extends AbstractEquoRule<EquoRule> {

  private IEclipseContext eclipseContext;
  private MApplication app;
  private WorkbenchRendererFactory rendererFactory;
  private CommandProcessingAddon commandAddon;
  private HandlerProcessingAddon handlerAddon;
  private ModelServiceImpl modelService;
  private List<Shell> preExistingShells;

  private boolean runWithE4Model;

  public EquoRule(Object testCase) {
    super(testCase);
    preExistingShells = Arrays.asList(captureShells());
  }

  @Override
  protected Optional<Statement> additionalStatements(Statement base) {
    if (runWithE4Model) {
      return Optional.of(new RunWithE4ModelStatement(base, app, eclipseContext, getDisplay()));
    }

    return Optional.empty();
  }

  public Shell createShell() {
    return createShell(SWT.SHELL_TRIM);
  }

  public Shell createShell(int style) {
    return new Shell(getDisplay(), style);
  }

  public EquoRule withApplicationContext(MApplication app) {
    return withApplicationContext(app, null);
  }

  /**
   * Initializes the application context using the given application model and
   * workbench renderer factory.
   * @return this
   */
  @SuppressWarnings({ "rawtypes", "unused" })
  public EquoRule withApplicationContext(MApplication app,
      WorkbenchRendererFactory rendererFactory) {
    this.rendererFactory = rendererFactory;
    eclipseContext = EclipseContextFactory
        .getServiceContext(FrameworkUtil.getBundle(this.getClass()).getBundleContext());

    if (rendererFactory != null) {
      rendererFactory.init(eclipseContext);
    }

    IContributionFactory factory = new ReflectionContributionFactory();
    eclipseContext.set(IContributionFactory.class, factory);

    IResourceUtilities resources = new ResourceUtility();
    eclipseContext.set(IResourceUtilities.class, resources);

    Logger logger = mock(Logger.class);

    eclipseContext.set(Logger.class, logger);

    app.setContext(eclipseContext);
    eclipseContext.set(MApplication.class, app);

    modelService = new ModelServiceImpl(eclipseContext);
    eclipseContext.set(EModelService.class, modelService);

    if (runWithE4Model) {
      IWorkbench wb = new E4Workbench((MApplicationElement) app, eclipseContext);
    }

    CommandServiceImpl commandService = new CommandServiceImpl();
    CommandManager commandManager = new CommandManager();
    commandService.setManager(commandManager);
    eclipseContext.set(ECommandService.class, commandService);

    UISynchronize sync = new UISynchronize() {
      @Override
      public void syncExec(Runnable runnable) {
        runnable.run();
      }

      @Override
      public void asyncExec(Runnable runnable) {
        runnable.run();
      }
    };
    eclipseContext.set(UISynchronize.class, sync);

    IEventBroker eventBroker = mock(IEventBroker.class);
    eclipseContext.set(IEventBroker.class, eventBroker);

    EHandlerService handlerService = new HandlerServiceImpl();
    eclipseContext.set(EHandlerService.class, handlerService);

    getDisplay().syncExec(() -> {
      Composite parent = new Composite(createShell(), SWT.NONE);
    });

    IEclipseContext addonStaticContext = EclipseContextFactory.create();
    for (MAddon addon : app.getAddons()) {
      addonStaticContext.set(MAddon.class, addon);
      Object obj = factory.create(addon.getContributionURI(), eclipseContext, addonStaticContext);
      addon.setObject(obj);
      if (addon.getElementId().equals("org.eclipse.e4.ui.workbench.commands.model")) {
        commandAddon = (CommandProcessingAddon) obj;
      }
      if (addon.getElementId().equals("org.eclipse.e4.ui.workbench.handler.model")) {
        handlerAddon = (HandlerProcessingAddon) obj;
      }
    }

    return this;
  }

  public IEclipseContext getEclipseContext() {
    return eclipseContext;
  }

  public WorkbenchRendererFactory getRendererFactory() {
    return rendererFactory;
  }

  public CommandProcessingAddon getCommandAddon() {
    return commandAddon;
  }

  public HandlerProcessingAddon getHandlerAddon() {
    return handlerAddon;
  }

  public ModelServiceImpl getModelService() {
    return modelService;
  }

  public MApplication getMApplication() {
    return app;
  }

  public void additionalDisposes() {
    disposeNewShells();
  }

  /**
   * Initializes an E4Workbench. IEventBroker is mocked so UI events will not be
   * notified from the model to the renderers. Internally calls
   * withApplicationContext with the application obtained from the model. The
   * renderer factory is initialized by the IPresentationEngine.
   * @param  modelPath path to the e4 model to be used
   * @return this
   */
  public EquoRule runWithE4Model(String modelPath) {
    runWithE4Model = true;
    runInNonUiThread = false;
    this.app = ModelHelper.getModelApplication(modelPath);
    withApplicationContext(app);
    return this;
  }

  private static Shell[] captureShells() {
    Shell[] result = new Shell[0];
    Display currentDisplay = Display.getCurrent();
    if (currentDisplay != null) {
      result = currentDisplay.getShells();
    }
    return result;
  }

  /**
   * Captures shells from the current display and returns only the newly created
   * shells.
   */
  public List<Shell> getNewShells() {
    List<Shell> newShells = new ArrayList<>();
    Shell[] shells = captureShells();
    for (Shell shell : shells) {
      if (!preExistingShells.contains(shell)) {
        newShells.add(shell);
      }
    }
    return newShells;
  }

  private void disposeNewShells() {
    List<Shell> newShells = getNewShells();
    for (Shell shell : newShells) {
      shell.dispose();
    }
  }

}
