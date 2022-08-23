package mirror.normalasm.proxy;

import mirror.normalasm.bakedquad.NormalVertexDataPool;
import mirror.normalasm.core.NormalTransformer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraftforge.client.resource.ISelectiveResourceReloadListener;
import net.minecraftforge.client.resource.VanillaResourceType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import pl.asie.foamfix.shared.FoamFixShared;
import slimeknights.tconstruct.library.client.texture.AbstractColoredTexture;
import mirror.normalasm.NormalLogger;
import mirror.normalasm.NormalReflector;
import mirror.normalasm.client.mcfixes.SkinDataReleaser;
import mirror.normalasm.client.models.bucket.NormalBakedDynBucket;
import mirror.normalasm.client.screenshot.ScreenshotListener;
import mirror.normalasm.client.sprite.FramesTextureData;
import mirror.normalasm.common.modfixes.qmd.QMDEventHandler;
import mirror.normalasm.config.NormalConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = "normalasm", value = Side.CLIENT)
public class ClientProxy extends CommonProxy {

    public static final List<Runnable> refreshAfterModels = new ArrayList<>();
    public static final boolean flushTinkerSpriteFrameTextureData;

    static {
        boolean static$flushTinkerSpriteFrameTextureData = true;
        if (Loader.isModLoaded("tconstruct") && Loader.isModLoaded("foamfix")) {
            if (FoamFixShared.config.clDynamicItemModels) {
                static$flushTinkerSpriteFrameTextureData = false;
            }
        }
        flushTinkerSpriteFrameTextureData = static$flushTinkerSpriteFrameTextureData;
    }

    public static boolean canReload = true;

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        if (NormalConfig.instance.releaseSpriteFramesCache) {
            MinecraftForge.EVENT_BUS.register(FramesTextureData.class);
        }
        if (Loader.isModLoaded("qmd") && NormalConfig.instance.optimizeQMDBeamRenderer) {
            MinecraftForge.EVENT_BUS.register(QMDEventHandler.class);
        }
        if (NormalConfig.instance.copyScreenshotToClipboard) {
            MinecraftForge.EVENT_BUS.register(ScreenshotListener.class);
        }
        if (NormalConfig.instance.fixMC186052) {
            MinecraftForge.EVENT_BUS.register(SkinDataReleaser.class);
        }
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        if (!Loader.isModLoaded("jei")) {
            releaseSpriteFramesCache();
        }
    }

    @Override
    public void loadComplete(FMLLoadCompleteEvent event) {
        super.loadComplete(event);
        if (Loader.isModLoaded("jei")) {
            releaseSpriteFramesCache();
        }
        if (!NormalTransformer.isOptifineInstalled && NormalConfig.instance.vertexDataCanonicalization) {
            NormalLogger.instance.info("{} total quads processed. {} unique vertex data array in NormalVertexDataPool, {} vertex data arrays deduplicated altogether during game load.", NormalVertexDataPool.getDeduplicatedCount(), NormalVertexDataPool.getSize(), NormalVertexDataPool.getDeduplicatedCount() - NormalVertexDataPool.getSize());
            MinecraftForge.EVENT_BUS.register(NormalVertexDataPool.class);
        }
    }

    private void releaseSpriteFramesCache() {
        ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener((ISelectiveResourceReloadListener) (manager, predicate) -> {
            if (predicate.test(VanillaResourceType.MODELS)) {
                refreshAfterModels.forEach(Runnable::run);
                if (NormalConfig.instance.reuseBucketQuads) {
                    NormalBakedDynBucket.baseQuads.clear();
                    NormalBakedDynBucket.flippedBaseQuads.clear();
                    NormalBakedDynBucket.coverQuads.clear();
                    NormalBakedDynBucket.flippedCoverQuads.clear();
                }
                if (NormalConfig.instance.releaseSpriteFramesCache) {
                    canReload = false;
                    try {
                        for (TextureAtlasSprite sprite : ((Map<String, TextureAtlasSprite>) NormalReflector.resolveFieldGetter(TextureMap.class, "mapRegisteredSprites", "field_110574_e").invoke(Minecraft.getMinecraft().getTextureMapBlocks())).values()) {
                            if (!sprite.hasAnimationMetadata()) {
                                if (!flushTinkerSpriteFrameTextureData && sprite instanceof AbstractColoredTexture) {
                                    continue;
                                }
                                sprite.setFramesTextureData(new FramesTextureData(sprite));
                            }
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                    canReload = true;
                }
                if (!NormalTransformer.isOptifineInstalled && NormalConfig.instance.vertexDataCanonicalization) {
                    NormalVertexDataPool.invalidate();
                }
            }
        });
    }
}
