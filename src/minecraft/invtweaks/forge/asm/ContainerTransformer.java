package invtweaks.forge.asm;

import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import cpw.mods.fml.relauncher.FMLRelaunchLog;
import cpw.mods.fml.relauncher.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.HashMap;
import java.util.Map;


public class ContainerTransformer implements IClassTransformer {
    public static final String VALID_INVENTORY_METHOD = "invtweaks$validInventory";
    public static final String VALID_CHEST_METHOD = "invtweaks$validChest";
    public static final String STANDARD_INVENTORY_METHOD = "invtweaks$standardInventory";
    public static final String ROW_SIZE_METHOD = "invtweaks$rowSize";
    public static final String SLOT_MAP_METHOD = "invtweaks$slotMap";
    public static final String CONTAINER_CLASS_INTERNAL = "net/minecraft/inventory/Container";
    public static final String SLOT_MAPS_VANILLA_CLASS = "invtweaks/containers/VanillaSlotMaps";
    public static final String SLOT_MAPS_MODCOMPAT_CLASS = "invtweaks/containers/CompatibilitySlotMaps";

    private static Map<String, ContainerInfo> standardClasses = new HashMap<String, ContainerInfo>();
    private static Map<String, ContainerInfo> compatibilityClasses = new HashMap<String, ContainerInfo>();
    private String containerClassName;

    public ContainerTransformer() {
    }

    // This needs to have access to the FML remapper so it needs to run when we know it's been set up correctly.
    private void lateInit() {
        // TODO: ContainerCreative handling
        // Standard non-chest type
        standardClasses.put("net.minecraft.inventory.ContainerPlayer",
                            new ContainerInfo(true, true, false, getVanillaSlotMapInfo("containerPlayerSlots")));
        standardClasses.put("net.minecraft.inventory.ContainerMerchant", new ContainerInfo(true, true, false));
        standardClasses.put("net.minecraft.inventory.ContainerRepair",
                            new ContainerInfo(true, true, false, getVanillaSlotMapInfo("containerPlayerSlots")));
        standardClasses.put("net.minecraft.inventory.ContainerHopper", new ContainerInfo(true, true, false));
        standardClasses.put("net.minecraft.inventory.ContainerBeacon", new ContainerInfo(true, true, false));
        standardClasses.put("net.minecraft.inventory.ContainerBrewingStand",
                            new ContainerInfo(true, true, false, getVanillaSlotMapInfo("containerBrewingSlots")));
        standardClasses.put("net.minecraft.inventory.ContainerWorkbench",
                            new ContainerInfo(true, true, false, getVanillaSlotMapInfo("containerWorkbenchSlots")));
        standardClasses.put("net.minecraft.inventory.ContainerEnchantment",
                            new ContainerInfo(true, true, false, getVanillaSlotMapInfo("containerEnchantmentSlots")));
        standardClasses.put("net.minecraft.inventory.ContainerFurnace",
                            new ContainerInfo(true, true, false, getVanillaSlotMapInfo("containerFurnaceSlots")));

        // Chest-type
        standardClasses.put("net.minecraft.inventory.ContainerDispenser",
                            new ContainerInfo(false, false, true, (short) 3,
                                              getVanillaSlotMapInfo("containerChestDispenserSlots")));
        standardClasses.put("net.minecraft.inventory.ContainerChest", new ContainerInfo(false, false, true,
                                                                                        getVanillaSlotMapInfo(
                                                                                                "containerChestDispenserSlots")));

        // Mod compatibility
        compatibilityClasses.put("micdoodle8.mods.galacticraft.core.inventory.GCCoreContainerPlayer",
                                 new ContainerInfo(true, true, false,
                                                   getCompatiblitySlotMapInfo("galacticraftPlayerSlots")));
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if(containerClassName == null) {
            if(FMLPlugin.runtimeDeobfEnabled) {
                containerClassName = FMLDeobfuscatingRemapper.INSTANCE.unmap(CONTAINER_CLASS_INTERNAL);
            } else {
                containerClassName = CONTAINER_CLASS_INTERNAL;
            }
            lateInit();
        }

        if("net.minecraft.inventory.Container".equals(transformedName)) {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode(Opcodes.ASM4);
            cr.accept(cn, 0);

            FMLRelaunchLog.info("InvTweaks: %s", transformedName);

            transformBaseContainer(cn);

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            return cw.toByteArray();
        }

        // Transform classes with explicitly specified information
        ContainerInfo info = standardClasses.get(transformedName);
        if(info != null) {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode(Opcodes.ASM4);
            cr.accept(cn, 0);

            FMLRelaunchLog.info("InvTweaks: %s", transformedName);

            transformContainer(cn, info);

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            return cw.toByteArray();
        }

        if("invtweaks.InvTweaksObfuscation".equals(transformedName)) {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode(Opcodes.ASM4);
            cr.accept(cn, 0);

            FMLRelaunchLog.info("InvTweaks: %s", transformedName);

            Type containertype =
                    Type.getObjectType(containerClassName);
            for(MethodNode method : cn.methods) {
                if("isValidChest".equals(method.name)) {
                    ASMHelper.replaceSelfForwardingMethod(method, VALID_CHEST_METHOD, containertype);
                } else if("isValidInventory".equals(method.name)) {
                    ASMHelper.replaceSelfForwardingMethod(method, VALID_INVENTORY_METHOD, containertype);
                } else if("isStandardInventory".equals(method.name)) {
                    ASMHelper.replaceSelfForwardingMethod(method, STANDARD_INVENTORY_METHOD, containertype);
                } else if("getSpecialChestRowSize".equals(method.name)) {
                    ASMHelper.replaceSelfForwardingMethod(method, ROW_SIZE_METHOD, containertype);
                } else if("getContainerSlotMap".equals(method.name)) {
                    ASMHelper.replaceSelfForwardingMethod(method, SLOT_MAP_METHOD, containertype);
                }
            }

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            return cw.toByteArray();
        }

        // TODO: Check API annotations here

        info = compatibilityClasses.get(transformedName);
        if(info != null) {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode(Opcodes.ASM4);
            cr.accept(cn, 0);

            FMLRelaunchLog.info("InvTweaks: %s", transformedName);

            transformContainer(cn, info);

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            return cw.toByteArray();
        }

        return bytes;
    }

    /**
     * Alter class to contain information contained by ContainerInfo
     *
     * @param clazz Class to alter
     * @param info  Information used to alter class
     */
    public static void transformContainer(ClassNode clazz, ContainerInfo info) {
        ASMHelper.generateBooleanMethodConst(clazz, STANDARD_INVENTORY_METHOD, info.standardInventory);
        ASMHelper.generateBooleanMethodConst(clazz, VALID_INVENTORY_METHOD, info.validInventory);
        ASMHelper.generateBooleanMethodConst(clazz, VALID_CHEST_METHOD, info.validChest);
        ASMHelper.generateIntegerMethodConst(clazz, ROW_SIZE_METHOD, info.rowSize);
        if(info.slotMapMethod.isStatic) {
            ASMHelper.generateForwardingToStaticMethod(clazz, SLOT_MAP_METHOD, info.slotMapMethod.methodName,
                                                       info.slotMapMethod.methodType.getReturnType(),
                                                       info.slotMapMethod.methodClass,
                                                       info.slotMapMethod.methodType.getArgumentTypes()[0]);
        } else {
            ASMHelper.generateSelfForwardingMethod(clazz, SLOT_MAP_METHOD, info.slotMapMethod.methodName,
                                                   info.slotMapMethod.methodType);
        }
    }


    /**
     * Alter class to contain default implementations of added methods.
     *
     * @param clazz Class to alter
     */
    public static void transformBaseContainer(ClassNode clazz) {
        ASMHelper.generateBooleanMethodConst(clazz, STANDARD_INVENTORY_METHOD, false);
        ASMHelper.generateDefaultInventoryCheck(clazz);
        ASMHelper.generateBooleanMethodConst(clazz, VALID_CHEST_METHOD, false);
        ASMHelper.generateIntegerMethodConst(clazz, ROW_SIZE_METHOD, (short) 9);
        ASMHelper.generateForwardingToStaticMethod(clazz, SLOT_MAP_METHOD, "unknownContainerSlots",
                                                   Type.getObjectType("java/util/Map"),
                                                   Type.getObjectType(SLOT_MAPS_VANILLA_CLASS));
    }

    private MethodInfo getCompatiblitySlotMapInfo(String name) {
        return getSlotMapInfo(Type.getObjectType(SLOT_MAPS_MODCOMPAT_CLASS), name, true);
    }

    private MethodInfo getVanillaSlotMapInfo(String name) {
        return getSlotMapInfo(Type.getObjectType(SLOT_MAPS_VANILLA_CLASS), name, true);
    }

    private MethodInfo getSlotMapInfo(Type mClass, String name, boolean isStatic) {
        return new MethodInfo(Type.getMethodType(
                Type.getObjectType("java/util/Map"),
                Type.getObjectType(containerClassName)),
                              mClass, name, true);
    }

    class MethodInfo {
        Type methodType;
        Type methodClass;
        String methodName;
        boolean isStatic = false;

        MethodInfo(Type mType, Type mClass, String name) {
            methodType = mType;
            methodClass = mClass;
            methodName = name;
        }

        MethodInfo(Type mType, Type mClass, String name, boolean stat) {
            methodType = mType;
            methodClass = mClass;
            methodName = name;
            isStatic = stat;
        }
    }

    class ContainerInfo {
        boolean standardInventory = false;
        boolean validInventory = false;
        boolean validChest = false;
        short rowSize = 9;
        MethodInfo slotMapMethod = getVanillaSlotMapInfo("unknownContainerSlots");

        ContainerInfo() {
        }

        ContainerInfo(boolean standard, boolean validInv, boolean validCh) {
            standardInventory = standard;
            validInventory = validInv;
            validChest = validCh;
        }

        ContainerInfo(boolean standard, boolean validInv, boolean validCh, MethodInfo slotMap) {
            standardInventory = standard;
            validInventory = validInv;
            validChest = validCh;
            slotMapMethod = slotMap;
        }

        ContainerInfo(boolean standard, boolean validInv, boolean validCh, short rowS) {
            standardInventory = standard;
            validInventory = validInv;
            validChest = validCh;
            rowSize = rowS;
        }

        ContainerInfo(boolean standard, boolean validInv, boolean validCh, short rowS, MethodInfo slotMap) {
            standardInventory = standard;
            validInventory = validInv;
            validChest = validCh;
            rowSize = rowS;
            slotMapMethod = slotMap;
        }
    }
}
