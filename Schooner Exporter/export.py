# USAGE NOTES:
# - Meshes must be quads OR tris, not mixed.
# - All rotations must be in quaternions
# - Do NOT place a keyframe on frame zero. (Blender starts 
#   at frame 1 by default, so this shouldn't be a problem.)
# - Unused bones (bones with no parented vertices) should not have used children.
# - Bone scale is ignored

import bpy

from struct import Struct

class InfoOperator(bpy.types.Operator):
	""" This Operator notifies the user with an info blurb in the header
	"""
	bl_idname = "ui.info"
	bl_label = "Invoke Info Operator"

	text = bpy.props.StringProperty()

	def execute(self, context):
		self.report({'INFO'}, self.text)
		return {'FINISHED'}

class WarnOperator(bpy.types.Operator):
	""" This Operator notifies the user with a a warning blurb in the header
	"""
	bl_idname = "ui.warn"
	bl_label = "Invoke Warn Operator"

	text = bpy.props.StringProperty()

	def execute(self, context):
		self.report({'WARNING'}, self.text)
		return {'FINISHED'}

bpy.utils.register_class(InfoOperator)
bpy.utils.register_class(WarnOperator)

def info(text=""):
	bpy.ops.ui.info('EXEC_DEFAULT', text=text)

def warn(text=""):
	bpy.ops.ui.warn('EXEC_DEFAULT', text=text)

class BinFile:
	endian = '>'
	
	signedByte = Struct(endian + 'b')
	unsignedByte = Struct(endian + 'B')
	
	signedShort = Struct(endian + 'h')
	unsignedShort = Struct(endian + 'H')
	
	signedFloat = Struct(endian + 'f')
	
	signedInt = Struct(endian + 'i')
	
	def __init__(self, directory, name):
		filepath = directory + name
		import os
		if not os.path.exists(directory):
			os.mkdir(directory)
		if os.path.exists(filepath):
			self.file = open(filepath, "wb")
		else:
			self.file = open(filepath, "ab")
		
	def writeFlags(self, bools, bytecount=1):
		written = 0
		while bytecount > 0:
			flags = 0
			for i in range(min(8, len(bools) - written)):
				if bools[written + i]:
					flags |= 2 ** i
			
			self.file.write(BinFile.unsignedByte.pack(flags))
			
			written += 8
			bytecount -= 1
	
	def writeByte(self, b, signed=False):
		if signed:
			self.file.write(BinFile.signedByte.pack(b))
		else:
			self.file.write(BinFile.unsignedByte.pack(b))
	
	def writeShort(self, s, signed=False):
		if signed:
			self.file.write(BinFile.signedShort.pack(s))
		else:
			self.file.write(BinFile.unsignedShort.pack(s))
	
	def writeInt(self, i):
		self.file.write(BinFile.signedInt.pack(i))
		
	def writeFloat(self, f):
		self.file.write(BinFile.signedFloat.pack(f))
		
	def writeString(self, string):
		self.file.write(bytes(string + chr(0), "UTF-8"))
		
	def writeAllShorts(self, shorts, signed=False):
		for s in shorts:
			if signed:
				self.file.write(BinFile.unsignedShort.pack(s))
			else:
				self.file.write(BinFile.signedShort.pack(s))
	
	def writeAllShortPairs(self, shortPairs, signed=False):
		for pair in shortPairs:
			for s in pair:
				if signed:
					self.file.write(BinFile.unsignedShort.pack(s))
				else:
					self.file.write(BinFile.signedShort.pack(s))
	
	def writeAllFloats(self, floats):
		for f in floats:
			self.file.write(BinFile.signedFloat.pack(f))
	
	def close(self):
		self.file.close()
		self.file=None

class MeshExporter:
	def __init__(self, mesh_object, tris=True, textured=False, armature_indexed=False):
		bpy.context.scene.objects.active = mesh_object
		mesh_object.select = True
		self.setMode('EDIT')
		bpy.ops.mesh.select_all(action='SELECT')
		if tris:
			bpy.ops.mesh.quads_convert_to_tris()
		else:
			bpy.ops.mesh.tris_convert_to_quads()
		bpy.ops.mesh.remove_doubles()
		
		mesh = mesh_object.data
		
		if textured:
			# select the seam edges
			seams = [edge for edge in mesh.edges if edge.use_seam and not edge.use_edge_sharp]
			self.select(seams)
			bpy.ops.mesh.edge_split()
			# get selected edges (result of split)
			self.seams = [edge for edge in mesh.edges if edge.select]
			# get the vertex indices in the selected edges
			seamIndices = []
			for edge in self.seams:
				for vert_index in edge.vertices:
					if not seamIndices.count(vert_index):
						seamIndices.append(vert_index)
			
			# for each possible pair of indices, compare the vertices
			# and write those indices to doubles if the vertices coincide
			self.doubles = []
			for indexA in seamIndices:
				for indexB in seamIndices[seamIndices.find(indexA)+1:]:
					vertA = self.mesh.vertices[indexA]
					vertB = self.mesh.vertices[indexB]
					match = [a == b for a, b in zip(vertA.co, vertB.co)]
					if match[0] and match[1] and match[2]:
						self.doubles.append((indexA, indexB))
		
		# split sharp edges. We don't have to worry about doubles because we want these to be sharp.
		sharp = [edge for edge in mesh.edges if edge.use_edge_sharp]
		self.select(sharp)
		bpy.ops.mesh.edge_split()
		
		# store flags
		self.tris = tris
		self.textured = textured
		self.armature_indexed = armature_indexed
		
		# store index data
		self.indices = [index for face in mesh.polygons for index in face.vertices]
		
		# store vertex data
		self.vertices = [coord for vert in mesh.vertices for coord in vert.co]
		
		# store UV data
		if textured:
			bpy.ops.mesh.select_all(action='SELECT')
			mesh.calc_tessface()
			self.uvs = []
			for vertIndex in range(len(mesh.vertices)):
				for face in mesh.polygons:
					uvs_found = False
					for value, i in zip(face.vertices, range(len(face.vertices))):
						if value == vertIndex:
							uv = self.getUVs(mesh, mesh.polygons.find(face), i)
							self.uvs.extend(uv)
							uvs_found = True
							break
					if uvs_found:
						break
		
		# store armature weight data
		if armature_indexed:
			self.bone_weights = []
			for vertex, vert_index in zip(mesh.vertices, range(len(mesh.vertices))):
				bones = []
				for g in vertex.groups:
					if g.weight:
						bone = (mesh_object.parent.data.bones.find(mesh_object.vertex_groups[g.group].name), g.weight)
						bones.append(bone)
				self.armature_indices.append(bones)
				
	
	def export(self, directory, name):
		file = BinFile(directory, name + ".sch3Dmesh")
		file.writeInt(1)
		
		# write flags
		file.writeFlags((self.tris, self.textured, self.armature_indexed))
		
		# write number of faces
		if self.tris:
			file.writeShort(int(len(self.indices) / 3))
		else:
			file.writeShort(int(len(self.indices) / 4))
			
		# write number of vertices
		file.writeShort(int(len(self.vertices) / 3))
		
		# write indices
		file.writeAllShorts(self.indices)
		
		# write vertices
		file.writeAllFloats(self.vertices)
		
		if self.textured:
			# write doubles
			file.writeShort(len(self.doubles))
			file.writeAllShortPairs(self.doubles)
			# write UVs
			file.writeAllFloats(self.uvs)
		
		if self.armature_indexed:
			# write armature weights
			for vertex_bones in self.bone_weights:
				file.writeByte(len(vertex_bones))
				for index, weight in vertex_bones:
					file.writeByte(index)
					file.writeFloat(weight)
		
		# close file
		file.close()
	
	def getUVs(self, mesh, faceIndex=0, uvIndex=0):
		return mesh.tessface_uv_textures.active.data[faceIndex].uv[uvIndex*2:uvIndex*2+2]
	
	def select(self, edges, extend=False):
		originalMode = bpy.context.active_object.mode
		if not extend:
			self.setMode(mode='EDIT')
			bpy.ops.mesh.select_all(action='DESELECT')
		self.setMode(mode='OBJECT')
		for edge in edges:
			edge.select=True
		self.setMode(originalMode)
	
	def setMode(self, mode):
		bpy.ops.object.mode_set(mode=mode)
		
class ArmatureExporter:
	def __init__(self, armature_object, actions, exportMovements=True):
		self.actions = actions
		self.armature = armature_object.data
		self.bones = []
		for bone in self.armature.bones:
			for child in armature_object.children:
				if child.vertex_groups and child.vertex_groups.get(bone.name):
					self.bones.append(bone)
		
	def export(self, directory, name, options):
		file = BinFile(directory, name + ".sch3Darmature")
		file.writeInt(1)
		
		file.writeByte(len(self.bones))
		
		for bone in self.bones:
			file.writeAllFloats([bone.tail_local[i] - bone.head_local[i] for i in range(3)])
			if bone.parent:
				file.writeByte(self.bones.index(bone.parent) + 1)
			else:
				file.writeByte(0) # Root parent
		
		for action in self.actions:
			if options.moveScale:
				scale='UNIFORM'
			elif options.moveScaleAxis:
				scale='AXIS'
			else:
				scale='NONE'
			
			writeMovementToFile(action, file, options.moveLoc, options.moveRot, scale)
			
			boneCurves = ["rotation_quaternion",]
			for bone in self.bones:
				group = action.groups.get(bone.name)
				if group:
					for fcurve in group.channels:
						if boneCurves.count(fcurve.data_path):
							writeFCurveToFile(fcurve, file)
				else:
					file.writeFloat(0.0)
				
		file.close()

class ArmatureOptions:
	
	def __init__(self, moveLoc=True, moveRot=True, moveScaleAxis=False, moveScale=True):
		self.moveLoc = moveLoc
		self.moveRot = moveRot
		self.moveScaleAxis = moveScaleAxis
		self.moveScale = moveScale

def getActionsOfArmature(armature_object):
	if verbose:
		print("Finding actions of " + armature_object.name)
	armature = armature_object.data
	actions = []
	for action in bpy.data.actions:
		if verbose:
			print("Check Action " + action.name)
		if action.name.rsplit(".",1)[0] != armature_object.name + "Action":
			if verbose:
				print("Not this action.")
			continue
		for group in action.groups:
			if verbose:
				print("Check group " + group.name)
			if not armature.bones.get(group.name):
				break
			for fcurve in group.channels:
				if fcurve.data_path.find('quaternion') + 1:
					if verbose:
						print("Quat rotation found. :)")
					break
			else:
				if verbose:
					print("No quat rotation found. :(")
				break
		else:
			actions.append(action)
	return actions

def getActionsOfObject(obj):
	actions = []
	for action in bpy.data.actions:
		if action.name.rsplit(".",1)[0] == obj.name + "Action":
			for fcurve in action.fcurves:
				if fcurve.data_path.find('quaternion') + 1:
					actions.append(action)
					break
	
	if len(actions):
		return actions
	else:
		return None

def exportActions(directory, name, actions):
	# Exports the given list of actions to a .sch3Dmovements file with the given name in the given directory
	file = BinFile(directory, name + ".sch3Dmovements")
	file.writeInt(1)
	for action in actions:
		writeMovementToFile(action, file, loc=True, rot=True, scale='UNIFORM') # Only supports uniform scale for now
	file.close()

def writeMovementToFile(action, file, loc=True, rot=True, scale='UNIFORM'):
	# name
	nameParts = action.name.rsplit(".",1)
	if len(nameParts) == 1: # action has no second part
		name = action.name
		warn("Action " + action.name + " has no second part. Will be named in file as \"" + name + "\".")
	else:
		name = nameParts[1]
	
	# flags
	flags = (loc, rot, scale=='UNIFORM', scale=='AXIS')
	
	# FCurve data_paths and array_indices to export
	curveNames = {}
	curveKeys = []
	if loc:
		curveNames["location"] = (0, 1, 2)
		curveKeys.append("location")
	if rot:
		curveNames["rotation_quaternion"] = (0, 1, 2, 3)
		curveKeys.append("rotation_quaternion")
	if scale=='UNIFORM':
		curveNames["scale"] = (0,)
		curveKeys.append("scale")
	elif scale=='AXIS':
		curveNames["scale"] = (0, 1, 2)
		curveKeys.append("scale")
	
	# FCurves to export, and number of keyframes - 1
	curves = []
	keyframeCount = -1;
	for key in curveKeys:
		for index in curveNames[key]:
			for fcurve in action.fcurves:
				if fcurve.data_path==key and fcurve.array_index==index:
					if keyframeCount < 0:
						keyframeCount = len(fcurve.keyframe_points)-1
					elif keyframeCount != len(fcurve.keyframe_points)-1:
						raise RuntimeError("Number of keyframes in FCurves is not uniform for Action \'" + action.name + "\'")
					curves.append(fcurve)
	
	file.writeString(name)
	print("NAME: " + name)
	file.writeFlags(flags)
	print("FLAGS: " + str(flags))
	file.writeByte(keyframeCount)
	print("KEYFRAMECOUNT " + str(keyframeCount))
	for curve in curves:
		writeFCurveToFile(curve, file)
		print("write curve " + curve.data_path + "[" + str(curve.array_index) + "]")

def writeFCurveToFile(fcurve, file):
	counter = 0
	i = 0
	
	for keyframe in fcurve.keyframe_points:
		# Write left handle
		if i > 0:
			file.writeAllFloats(keyframe.handle_left)
			if verbose and False:
				print(str(keyframe.handle_left))
			counter += 1
		# Write point
		file.writeAllFloats(keyframe.co)
		if verbose and False:
			print(str(keyframe.co))
		counter += 1
		# Write right handle
		if i < len(fcurve.keyframe_points)-1:
			file.writeAllFloats(keyframe.handle_right)
			if verbose and False:
				print(str(keyframe.handle_left))
			counter += 1
		i+= 1

# Begin script.
verbose = True
if verbose:
	print("BEGIN SCRIPT.")
scene = bpy.context.scene
if bpy.data.filepath == "" :
	warn("You must save the .blend first!")
blendFileName = bpy.data.filepath.rsplit("\\",1)[1].rsplit(".",1)[0]
directory = bpy.data.filepath.rsplit("\\",1)[0] + "\\"  + blendFileName + "_exports\\"

if verbose:
	print("Exporting to directory: " + directory)

bpy.ops.object.mode_set(mode='OBJECT')

#verbose = False
# Export movements.
movementSources = [obj for obj in scene.objects if ['MESH',].count(obj.type)]
for obj in movementSources:
	if obj.rotation_mode != 'QUATERNION':
		warn(obj.name + " is not in quaternion rotation mode. " +
			"Make sure all objects and actions use quaternions for rotations.")
		continue
	actions = getActionsOfObject(obj)
	if actions is not None:
		if verbose:
			print("Exporting " + obj.name + "'s actions.")
		exportActions(directory, obj.name, actions)

# Export meshes.
originalScene = bpy.context.scene
bpy.ops.scene.new(type='FULL_COPY')
scene = bpy.context.scene

mesh_objects = [obj for obj in scene.objects if obj.type == 'MESH']
bpy.ops.object.mode_set(mode='OBJECT')
for obj in mesh_objects:
	obj.select=False
for obj in mesh_objects:
	exporter = MeshExporter(obj)
	exporter.export(directory, obj.name.rsplit(".",1)[0])
bpy.ops.scene.delete()
scene = bpy.context.scene

verbose = True

# Export armatures
armature_objects = [obj for obj in scene.objects if obj.type == 'ARMATURE']
print("ARMATURES: " + str(armature_objects))
for obj in armature_objects:
	actions = getActionsOfArmature(obj)
	print(actions)
	if actions:
		exporter = ArmatureExporter(obj, actions)
		exporter.export(directory, obj.name, ArmatureOptions())

# End script	
if verbose:
	print("END SCRIPT.")