��
��
:
Add
x"T
y"T
z"T"
Ttype:
2	
x
Assign
ref"T�

value"T

output_ref"T�"	
Ttype"
validate_shapebool("
use_lockingbool(�
~
BiasAdd

value"T	
bias"T
output"T" 
Ttype:
2	"-
data_formatstringNHWC:
NHWCNCHW
8
Const
output"dtype"
valuetensor"
dtypetype
.
Identity

input"T
output"T"	
Ttype
N
IsVariableInitialized
ref"dtype�
is_initialized
"
dtypetype�
p
MatMul
a"T
b"T
product"T"
transpose_abool( "
transpose_bbool( "
Ttype:
	2
e
MergeV2Checkpoints
checkpoint_prefixes
destination_prefix"
delete_old_dirsbool(�
=
Mul
x"T
y"T
z"T"
Ttype:
2	�

NoOp
M
Pack
values"T*N
output"T"
Nint(0"	
Ttype"
axisint 
C
Placeholder
output"dtype"
dtypetype"
shapeshape:
~
RandomUniform

shape"T
output"dtype"
seedint "
seed2int "
dtypetype:
2"
Ttype:
2	�
D
Relu
features"T
activations"T"
Ttype:
2	
o
	RestoreV2

prefix
tensor_names
shape_and_slices
tensors2dtypes"
dtypes
list(type)(0�
l
SaveV2

prefix
tensor_names
shape_and_slices
tensors2dtypes"
dtypes
list(type)(0�
H
ShardedFilename
basename	
shard

num_shards
filename
N

StringJoin
inputs*N

output"
Nint(0"
	separatorstring 
:
Sub
x"T
y"T
z"T"
Ttype:
2	
s

VariableV2
ref"dtype�"
shapeshape"
dtypetype"
	containerstring "
shared_namestring �"serve*1.9.02b'v1.9.0-0-g25c197e023'��
h
inputPlaceholder*'
_output_shapes
:���������*
dtype0*
shape:���������
O
outputPlaceholder*
_output_shapes
:*
dtype0*
shape:
q
dense_13_inputPlaceholder*'
_output_shapes
:���������*
dtype0*
shape:���������
n
dense_13/random_uniform/shapeConst*
valueB"      *
_output_shapes
:*
dtype0
`
dense_13/random_uniform/minConst*
valueB
 *�-ξ*
_output_shapes
: *
dtype0
`
dense_13/random_uniform/maxConst*
valueB
 *�-�>*
_output_shapes
: *
dtype0
�
%dense_13/random_uniform/RandomUniformRandomUniformdense_13/random_uniform/shape*
T0*
_output_shapes

:*
dtype0*
seed2���*
seed���)
}
dense_13/random_uniform/subSubdense_13/random_uniform/maxdense_13/random_uniform/min*
T0*
_output_shapes
: 
�
dense_13/random_uniform/mulMul%dense_13/random_uniform/RandomUniformdense_13/random_uniform/sub*
T0*
_output_shapes

:
�
dense_13/random_uniformAdddense_13/random_uniform/muldense_13/random_uniform/min*
T0*
_output_shapes

:
�
dense_13/kernel
VariableV2*
_output_shapes

:*
dtype0*
shared_name *
	container *
shape
:
�
dense_13/kernel/AssignAssigndense_13/kerneldense_13/random_uniform*
T0*
_output_shapes

:*
use_locking(*
validate_shape(*"
_class
loc:@dense_13/kernel
~
dense_13/kernel/readIdentitydense_13/kernel*
T0*
_output_shapes

:*"
_class
loc:@dense_13/kernel
[
dense_13/ConstConst*
valueB*    *
_output_shapes
:*
dtype0
y
dense_13/bias
VariableV2*
_output_shapes
:*
dtype0*
shared_name *
	container *
shape:
�
dense_13/bias/AssignAssigndense_13/biasdense_13/Const*
T0*
_output_shapes
:*
use_locking(*
validate_shape(* 
_class
loc:@dense_13/bias
t
dense_13/bias/readIdentitydense_13/bias*
T0*
_output_shapes
:* 
_class
loc:@dense_13/bias
�
dense_13/MatMulMatMuldense_13_inputdense_13/kernel/read*
transpose_b( *
T0*
transpose_a( *'
_output_shapes
:���������
�
dense_13/BiasAddBiasAdddense_13/MatMuldense_13/bias/read*
T0*
data_formatNHWC*'
_output_shapes
:���������
Y
dense_13/ReluReludense_13/BiasAdd*
T0*'
_output_shapes
:���������
n
dense_14/random_uniform/shapeConst*
valueB"      *
_output_shapes
:*
dtype0
`
dense_14/random_uniform/minConst*
valueB
 *�衾*
_output_shapes
: *
dtype0
`
dense_14/random_uniform/maxConst*
valueB
 *��>*
_output_shapes
: *
dtype0
�
%dense_14/random_uniform/RandomUniformRandomUniformdense_14/random_uniform/shape*
T0*
_output_shapes

:*
dtype0*
seed2���*
seed���)
}
dense_14/random_uniform/subSubdense_14/random_uniform/maxdense_14/random_uniform/min*
T0*
_output_shapes
: 
�
dense_14/random_uniform/mulMul%dense_14/random_uniform/RandomUniformdense_14/random_uniform/sub*
T0*
_output_shapes

:
�
dense_14/random_uniformAdddense_14/random_uniform/muldense_14/random_uniform/min*
T0*
_output_shapes

:
�
dense_14/kernel
VariableV2*
_output_shapes

:*
dtype0*
shared_name *
	container *
shape
:
�
dense_14/kernel/AssignAssigndense_14/kerneldense_14/random_uniform*
T0*
_output_shapes

:*
use_locking(*
validate_shape(*"
_class
loc:@dense_14/kernel
~
dense_14/kernel/readIdentitydense_14/kernel*
T0*
_output_shapes

:*"
_class
loc:@dense_14/kernel
[
dense_14/ConstConst*
valueB*    *
_output_shapes
:*
dtype0
y
dense_14/bias
VariableV2*
_output_shapes
:*
dtype0*
shared_name *
	container *
shape:
�
dense_14/bias/AssignAssigndense_14/biasdense_14/Const*
T0*
_output_shapes
:*
use_locking(*
validate_shape(* 
_class
loc:@dense_14/bias
t
dense_14/bias/readIdentitydense_14/bias*
T0*
_output_shapes
:* 
_class
loc:@dense_14/bias
�
dense_14/MatMulMatMuldense_13/Reludense_14/kernel/read*
transpose_b( *
T0*
transpose_a( *'
_output_shapes
:���������
�
dense_14/BiasAddBiasAdddense_14/MatMuldense_14/bias/read*
T0*
data_formatNHWC*'
_output_shapes
:���������
Y
dense_14/ReluReludense_14/BiasAdd*
T0*'
_output_shapes
:���������
n
dense_15/random_uniform/shapeConst*
valueB"      *
_output_shapes
:*
dtype0
`
dense_15/random_uniform/minConst*
valueB
 *�衾*
_output_shapes
: *
dtype0
`
dense_15/random_uniform/maxConst*
valueB
 *��>*
_output_shapes
: *
dtype0
�
%dense_15/random_uniform/RandomUniformRandomUniformdense_15/random_uniform/shape*
T0*
_output_shapes

:*
dtype0*
seed2���*
seed���)
}
dense_15/random_uniform/subSubdense_15/random_uniform/maxdense_15/random_uniform/min*
T0*
_output_shapes
: 
�
dense_15/random_uniform/mulMul%dense_15/random_uniform/RandomUniformdense_15/random_uniform/sub*
T0*
_output_shapes

:
�
dense_15/random_uniformAdddense_15/random_uniform/muldense_15/random_uniform/min*
T0*
_output_shapes

:
�
dense_15/kernel
VariableV2*
_output_shapes

:*
dtype0*
shared_name *
	container *
shape
:
�
dense_15/kernel/AssignAssigndense_15/kerneldense_15/random_uniform*
T0*
_output_shapes

:*
use_locking(*
validate_shape(*"
_class
loc:@dense_15/kernel
~
dense_15/kernel/readIdentitydense_15/kernel*
T0*
_output_shapes

:*"
_class
loc:@dense_15/kernel
[
dense_15/ConstConst*
valueB*    *
_output_shapes
:*
dtype0
y
dense_15/bias
VariableV2*
_output_shapes
:*
dtype0*
shared_name *
	container *
shape:
�
dense_15/bias/AssignAssigndense_15/biasdense_15/Const*
T0*
_output_shapes
:*
use_locking(*
validate_shape(* 
_class
loc:@dense_15/bias
t
dense_15/bias/readIdentitydense_15/bias*
T0*
_output_shapes
:* 
_class
loc:@dense_15/bias
�
dense_15/MatMulMatMuldense_14/Reludense_15/kernel/read*
transpose_b( *
T0*
transpose_a( *'
_output_shapes
:���������
�
dense_15/BiasAddBiasAdddense_15/MatMuldense_15/bias/read*
T0*
data_formatNHWC*'
_output_shapes
:���������
Y
dense_15/ReluReludense_15/BiasAdd*
T0*'
_output_shapes
:���������
n
dense_16/random_uniform/shapeConst*
valueB"   
   *
_output_shapes
:*
dtype0
`
dense_16/random_uniform/minConst*
valueB
 *�Kƾ*
_output_shapes
: *
dtype0
`
dense_16/random_uniform/maxConst*
valueB
 *�K�>*
_output_shapes
: *
dtype0
�
%dense_16/random_uniform/RandomUniformRandomUniformdense_16/random_uniform/shape*
T0*
_output_shapes

:
*
dtype0*
seed2���*
seed���)
}
dense_16/random_uniform/subSubdense_16/random_uniform/maxdense_16/random_uniform/min*
T0*
_output_shapes
: 
�
dense_16/random_uniform/mulMul%dense_16/random_uniform/RandomUniformdense_16/random_uniform/sub*
T0*
_output_shapes

:

�
dense_16/random_uniformAdddense_16/random_uniform/muldense_16/random_uniform/min*
T0*
_output_shapes

:

�
dense_16/kernel
VariableV2*
_output_shapes

:
*
dtype0*
shared_name *
	container *
shape
:

�
dense_16/kernel/AssignAssigndense_16/kerneldense_16/random_uniform*
T0*
_output_shapes

:
*
use_locking(*
validate_shape(*"
_class
loc:@dense_16/kernel
~
dense_16/kernel/readIdentitydense_16/kernel*
T0*
_output_shapes

:
*"
_class
loc:@dense_16/kernel
[
dense_16/ConstConst*
valueB
*    *
_output_shapes
:
*
dtype0
y
dense_16/bias
VariableV2*
_output_shapes
:
*
dtype0*
shared_name *
	container *
shape:

�
dense_16/bias/AssignAssigndense_16/biasdense_16/Const*
T0*
_output_shapes
:
*
use_locking(*
validate_shape(* 
_class
loc:@dense_16/bias
t
dense_16/bias/readIdentitydense_16/bias*
T0*
_output_shapes
:
* 
_class
loc:@dense_16/bias
�
dense_16/MatMulMatMuldense_15/Reludense_16/kernel/read*
transpose_b( *
T0*
transpose_a( *'
_output_shapes
:���������

�
dense_16/BiasAddBiasAdddense_16/MatMuldense_16/bias/read*
T0*
data_formatNHWC*'
_output_shapes
:���������

Y
dense_16/ReluReludense_16/BiasAdd*
T0*'
_output_shapes
:���������

n
dense_17/random_uniform/shapeConst*
valueB"
      *
_output_shapes
:*
dtype0
`
dense_17/random_uniform/minConst*
valueB
 *�5�*
_output_shapes
: *
dtype0
`
dense_17/random_uniform/maxConst*
valueB
 *�5?*
_output_shapes
: *
dtype0
�
%dense_17/random_uniform/RandomUniformRandomUniformdense_17/random_uniform/shape*
T0*
_output_shapes

:
*
dtype0*
seed2�+*
seed���)
}
dense_17/random_uniform/subSubdense_17/random_uniform/maxdense_17/random_uniform/min*
T0*
_output_shapes
: 
�
dense_17/random_uniform/mulMul%dense_17/random_uniform/RandomUniformdense_17/random_uniform/sub*
T0*
_output_shapes

:

�
dense_17/random_uniformAdddense_17/random_uniform/muldense_17/random_uniform/min*
T0*
_output_shapes

:

�
dense_17/kernel
VariableV2*
_output_shapes

:
*
dtype0*
shared_name *
	container *
shape
:

�
dense_17/kernel/AssignAssigndense_17/kerneldense_17/random_uniform*
T0*
_output_shapes

:
*
use_locking(*
validate_shape(*"
_class
loc:@dense_17/kernel
~
dense_17/kernel/readIdentitydense_17/kernel*
T0*
_output_shapes

:
*"
_class
loc:@dense_17/kernel
[
dense_17/ConstConst*
valueB*    *
_output_shapes
:*
dtype0
y
dense_17/bias
VariableV2*
_output_shapes
:*
dtype0*
shared_name *
	container *
shape:
�
dense_17/bias/AssignAssigndense_17/biasdense_17/Const*
T0*
_output_shapes
:*
use_locking(*
validate_shape(* 
_class
loc:@dense_17/bias
t
dense_17/bias/readIdentitydense_17/bias*
T0*
_output_shapes
:* 
_class
loc:@dense_17/bias
�
dense_17/MatMulMatMuldense_16/Reludense_17/kernel/read*
transpose_b( *
T0*
transpose_a( *'
_output_shapes
:���������
�
dense_17/BiasAddBiasAdddense_17/MatMuldense_17/bias/read*
T0*
data_formatNHWC*'
_output_shapes
:���������
Y
dense_17/ReluReludense_17/BiasAdd*
T0*'
_output_shapes
:���������
n
dense_18/random_uniform/shapeConst*
valueB"      *
_output_shapes
:*
dtype0
`
dense_18/random_uniform/minConst*
valueB
 *���*
_output_shapes
: *
dtype0
`
dense_18/random_uniform/maxConst*
valueB
 *��?*
_output_shapes
: *
dtype0
�
%dense_18/random_uniform/RandomUniformRandomUniformdense_18/random_uniform/shape*
T0*
_output_shapes

:*
dtype0*
seed2���*
seed���)
}
dense_18/random_uniform/subSubdense_18/random_uniform/maxdense_18/random_uniform/min*
T0*
_output_shapes
: 
�
dense_18/random_uniform/mulMul%dense_18/random_uniform/RandomUniformdense_18/random_uniform/sub*
T0*
_output_shapes

:
�
dense_18/random_uniformAdddense_18/random_uniform/muldense_18/random_uniform/min*
T0*
_output_shapes

:
�
dense_18/kernel
VariableV2*
_output_shapes

:*
dtype0*
shared_name *
	container *
shape
:
�
dense_18/kernel/AssignAssigndense_18/kerneldense_18/random_uniform*
T0*
_output_shapes

:*
use_locking(*
validate_shape(*"
_class
loc:@dense_18/kernel
~
dense_18/kernel/readIdentitydense_18/kernel*
T0*
_output_shapes

:*"
_class
loc:@dense_18/kernel
[
dense_18/ConstConst*
valueB*    *
_output_shapes
:*
dtype0
y
dense_18/bias
VariableV2*
_output_shapes
:*
dtype0*
shared_name *
	container *
shape:
�
dense_18/bias/AssignAssigndense_18/biasdense_18/Const*
T0*
_output_shapes
:*
use_locking(*
validate_shape(* 
_class
loc:@dense_18/bias
t
dense_18/bias/readIdentitydense_18/bias*
T0*
_output_shapes
:* 
_class
loc:@dense_18/bias
�
dense_18/MatMulMatMuldense_17/Reludense_18/kernel/read*
transpose_b( *
T0*
transpose_a( *'
_output_shapes
:���������
�
dense_18/BiasAddBiasAdddense_18/MatMuldense_18/bias/read*
T0*
data_formatNHWC*'
_output_shapes
:���������
\
PlaceholderPlaceholder*
_output_shapes

:*
dtype0*
shape
:
�
AssignAssigndense_13/kernelPlaceholder*
T0*
_output_shapes

:*
use_locking( *
validate_shape(*"
_class
loc:@dense_13/kernel
V
Placeholder_1Placeholder*
_output_shapes
:*
dtype0*
shape:
�
Assign_1Assigndense_13/biasPlaceholder_1*
T0*
_output_shapes
:*
use_locking( *
validate_shape(* 
_class
loc:@dense_13/bias
^
Placeholder_2Placeholder*
_output_shapes

:*
dtype0*
shape
:
�
Assign_2Assigndense_14/kernelPlaceholder_2*
T0*
_output_shapes

:*
use_locking( *
validate_shape(*"
_class
loc:@dense_14/kernel
V
Placeholder_3Placeholder*
_output_shapes
:*
dtype0*
shape:
�
Assign_3Assigndense_14/biasPlaceholder_3*
T0*
_output_shapes
:*
use_locking( *
validate_shape(* 
_class
loc:@dense_14/bias
^
Placeholder_4Placeholder*
_output_shapes

:*
dtype0*
shape
:
�
Assign_4Assigndense_15/kernelPlaceholder_4*
T0*
_output_shapes

:*
use_locking( *
validate_shape(*"
_class
loc:@dense_15/kernel
V
Placeholder_5Placeholder*
_output_shapes
:*
dtype0*
shape:
�
Assign_5Assigndense_15/biasPlaceholder_5*
T0*
_output_shapes
:*
use_locking( *
validate_shape(* 
_class
loc:@dense_15/bias
^
Placeholder_6Placeholder*
_output_shapes

:
*
dtype0*
shape
:

�
Assign_6Assigndense_16/kernelPlaceholder_6*
T0*
_output_shapes

:
*
use_locking( *
validate_shape(*"
_class
loc:@dense_16/kernel
V
Placeholder_7Placeholder*
_output_shapes
:
*
dtype0*
shape:

�
Assign_7Assigndense_16/biasPlaceholder_7*
T0*
_output_shapes
:
*
use_locking( *
validate_shape(* 
_class
loc:@dense_16/bias
^
Placeholder_8Placeholder*
_output_shapes

:
*
dtype0*
shape
:

�
Assign_8Assigndense_17/kernelPlaceholder_8*
T0*
_output_shapes

:
*
use_locking( *
validate_shape(*"
_class
loc:@dense_17/kernel
V
Placeholder_9Placeholder*
_output_shapes
:*
dtype0*
shape:
�
Assign_9Assigndense_17/biasPlaceholder_9*
T0*
_output_shapes
:*
use_locking( *
validate_shape(* 
_class
loc:@dense_17/bias
_
Placeholder_10Placeholder*
_output_shapes

:*
dtype0*
shape
:
�
	Assign_10Assigndense_18/kernelPlaceholder_10*
T0*
_output_shapes

:*
use_locking( *
validate_shape(*"
_class
loc:@dense_18/kernel
W
Placeholder_11Placeholder*
_output_shapes
:*
dtype0*
shape:
�
	Assign_11Assigndense_18/biasPlaceholder_11*
T0*
_output_shapes
:*
use_locking( *
validate_shape(* 
_class
loc:@dense_18/bias
�
IsVariableInitializedIsVariableInitializeddense_13/kernel*
_output_shapes
: *
dtype0*"
_class
loc:@dense_13/kernel
�
IsVariableInitialized_1IsVariableInitializeddense_13/bias*
_output_shapes
: *
dtype0* 
_class
loc:@dense_13/bias
�
IsVariableInitialized_2IsVariableInitializeddense_14/kernel*
_output_shapes
: *
dtype0*"
_class
loc:@dense_14/kernel
�
IsVariableInitialized_3IsVariableInitializeddense_14/bias*
_output_shapes
: *
dtype0* 
_class
loc:@dense_14/bias
�
IsVariableInitialized_4IsVariableInitializeddense_15/kernel*
_output_shapes
: *
dtype0*"
_class
loc:@dense_15/kernel
�
IsVariableInitialized_5IsVariableInitializeddense_15/bias*
_output_shapes
: *
dtype0* 
_class
loc:@dense_15/bias
�
IsVariableInitialized_6IsVariableInitializeddense_16/kernel*
_output_shapes
: *
dtype0*"
_class
loc:@dense_16/kernel
�
IsVariableInitialized_7IsVariableInitializeddense_16/bias*
_output_shapes
: *
dtype0* 
_class
loc:@dense_16/bias
�
IsVariableInitialized_8IsVariableInitializeddense_17/kernel*
_output_shapes
: *
dtype0*"
_class
loc:@dense_17/kernel
�
IsVariableInitialized_9IsVariableInitializeddense_17/bias*
_output_shapes
: *
dtype0* 
_class
loc:@dense_17/bias
�
IsVariableInitialized_10IsVariableInitializeddense_18/kernel*
_output_shapes
: *
dtype0*"
_class
loc:@dense_18/kernel
�
IsVariableInitialized_11IsVariableInitializeddense_18/bias*
_output_shapes
: *
dtype0* 
_class
loc:@dense_18/bias
�
initNoOp^dense_13/bias/Assign^dense_13/kernel/Assign^dense_14/bias/Assign^dense_14/kernel/Assign^dense_15/bias/Assign^dense_15/kernel/Assign^dense_16/bias/Assign^dense_16/kernel/Assign^dense_17/bias/Assign^dense_17/kernel/Assign^dense_18/bias/Assign^dense_18/kernel/Assign
P

save/ConstConst*
valueB Bmodel*
_output_shapes
: *
dtype0
�
save/StringJoin/inputs_1Const*<
value3B1 B+_temp_543824ff35ed4637ad5f5e1cc9de2380/part*
_output_shapes
: *
dtype0
u
save/StringJoin
StringJoin
save/Constsave/StringJoin/inputs_1*
	separator *
N*
_output_shapes
: 
Q
save/num_shardsConst*
value	B :*
_output_shapes
: *
dtype0
k
save/ShardedFilename/shardConst"/device:CPU:0*
value	B : *
_output_shapes
: *
dtype0
�
save/ShardedFilenameShardedFilenamesave/StringJoinsave/ShardedFilename/shardsave/num_shards"/device:CPU:0*
_output_shapes
: 
�
save/SaveV2/tensor_namesConst"/device:CPU:0*�
value�B�Bdense_13/biasBdense_13/kernelBdense_14/biasBdense_14/kernelBdense_15/biasBdense_15/kernelBdense_16/biasBdense_16/kernelBdense_17/biasBdense_17/kernelBdense_18/biasBdense_18/kernel*
_output_shapes
:*
dtype0
�
save/SaveV2/shape_and_slicesConst"/device:CPU:0*+
value"B B B B B B B B B B B B B *
_output_shapes
:*
dtype0
�
save/SaveV2SaveV2save/ShardedFilenamesave/SaveV2/tensor_namessave/SaveV2/shape_and_slicesdense_13/biasdense_13/kerneldense_14/biasdense_14/kerneldense_15/biasdense_15/kerneldense_16/biasdense_16/kerneldense_17/biasdense_17/kerneldense_18/biasdense_18/kernel"/device:CPU:0*
dtypes
2
�
save/control_dependencyIdentitysave/ShardedFilename^save/SaveV2"/device:CPU:0*
T0*
_output_shapes
: *'
_class
loc:@save/ShardedFilename
�
+save/MergeV2Checkpoints/checkpoint_prefixesPacksave/ShardedFilename^save/control_dependency"/device:CPU:0*

axis *
T0*
N*
_output_shapes
:
�
save/MergeV2CheckpointsMergeV2Checkpoints+save/MergeV2Checkpoints/checkpoint_prefixes
save/Const"/device:CPU:0*
delete_old_dirs(
�
save/IdentityIdentity
save/Const^save/MergeV2Checkpoints^save/control_dependency"/device:CPU:0*
T0*
_output_shapes
: 
�
save/RestoreV2/tensor_namesConst"/device:CPU:0*�
value�B�Bdense_13/biasBdense_13/kernelBdense_14/biasBdense_14/kernelBdense_15/biasBdense_15/kernelBdense_16/biasBdense_16/kernelBdense_17/biasBdense_17/kernelBdense_18/biasBdense_18/kernel*
_output_shapes
:*
dtype0
�
save/RestoreV2/shape_and_slicesConst"/device:CPU:0*+
value"B B B B B B B B B B B B B *
_output_shapes
:*
dtype0
�
save/RestoreV2	RestoreV2
save/Constsave/RestoreV2/tensor_namessave/RestoreV2/shape_and_slices"/device:CPU:0*
dtypes
2*D
_output_shapes2
0::::::::::::
�
save/AssignAssigndense_13/biassave/RestoreV2*
T0*
_output_shapes
:*
use_locking(*
validate_shape(* 
_class
loc:@dense_13/bias
�
save/Assign_1Assigndense_13/kernelsave/RestoreV2:1*
T0*
_output_shapes

:*
use_locking(*
validate_shape(*"
_class
loc:@dense_13/kernel
�
save/Assign_2Assigndense_14/biassave/RestoreV2:2*
T0*
_output_shapes
:*
use_locking(*
validate_shape(* 
_class
loc:@dense_14/bias
�
save/Assign_3Assigndense_14/kernelsave/RestoreV2:3*
T0*
_output_shapes

:*
use_locking(*
validate_shape(*"
_class
loc:@dense_14/kernel
�
save/Assign_4Assigndense_15/biassave/RestoreV2:4*
T0*
_output_shapes
:*
use_locking(*
validate_shape(* 
_class
loc:@dense_15/bias
�
save/Assign_5Assigndense_15/kernelsave/RestoreV2:5*
T0*
_output_shapes

:*
use_locking(*
validate_shape(*"
_class
loc:@dense_15/kernel
�
save/Assign_6Assigndense_16/biassave/RestoreV2:6*
T0*
_output_shapes
:
*
use_locking(*
validate_shape(* 
_class
loc:@dense_16/bias
�
save/Assign_7Assigndense_16/kernelsave/RestoreV2:7*
T0*
_output_shapes

:
*
use_locking(*
validate_shape(*"
_class
loc:@dense_16/kernel
�
save/Assign_8Assigndense_17/biassave/RestoreV2:8*
T0*
_output_shapes
:*
use_locking(*
validate_shape(* 
_class
loc:@dense_17/bias
�
save/Assign_9Assigndense_17/kernelsave/RestoreV2:9*
T0*
_output_shapes

:
*
use_locking(*
validate_shape(*"
_class
loc:@dense_17/kernel
�
save/Assign_10Assigndense_18/biassave/RestoreV2:10*
T0*
_output_shapes
:*
use_locking(*
validate_shape(* 
_class
loc:@dense_18/bias
�
save/Assign_11Assigndense_18/kernelsave/RestoreV2:11*
T0*
_output_shapes

:*
use_locking(*
validate_shape(*"
_class
loc:@dense_18/kernel
�
save/restore_shardNoOp^save/Assign^save/Assign_1^save/Assign_10^save/Assign_11^save/Assign_2^save/Assign_3^save/Assign_4^save/Assign_5^save/Assign_6^save/Assign_7^save/Assign_8^save/Assign_9
-
save/restore_allNoOp^save/restore_shard"<
save/Const:0save/Identity:0save/restore_all (5 @F8"�
trainable_variables��
`
dense_13/kernel:0dense_13/kernel/Assigndense_13/kernel/read:02dense_13/random_uniform:08
Q
dense_13/bias:0dense_13/bias/Assigndense_13/bias/read:02dense_13/Const:08
`
dense_14/kernel:0dense_14/kernel/Assigndense_14/kernel/read:02dense_14/random_uniform:08
Q
dense_14/bias:0dense_14/bias/Assigndense_14/bias/read:02dense_14/Const:08
`
dense_15/kernel:0dense_15/kernel/Assigndense_15/kernel/read:02dense_15/random_uniform:08
Q
dense_15/bias:0dense_15/bias/Assigndense_15/bias/read:02dense_15/Const:08
`
dense_16/kernel:0dense_16/kernel/Assigndense_16/kernel/read:02dense_16/random_uniform:08
Q
dense_16/bias:0dense_16/bias/Assigndense_16/bias/read:02dense_16/Const:08
`
dense_17/kernel:0dense_17/kernel/Assigndense_17/kernel/read:02dense_17/random_uniform:08
Q
dense_17/bias:0dense_17/bias/Assigndense_17/bias/read:02dense_17/Const:08
`
dense_18/kernel:0dense_18/kernel/Assigndense_18/kernel/read:02dense_18/random_uniform:08
Q
dense_18/bias:0dense_18/bias/Assigndense_18/bias/read:02dense_18/Const:08"�
	variables��
`
dense_13/kernel:0dense_13/kernel/Assigndense_13/kernel/read:02dense_13/random_uniform:08
Q
dense_13/bias:0dense_13/bias/Assigndense_13/bias/read:02dense_13/Const:08
`
dense_14/kernel:0dense_14/kernel/Assigndense_14/kernel/read:02dense_14/random_uniform:08
Q
dense_14/bias:0dense_14/bias/Assigndense_14/bias/read:02dense_14/Const:08
`
dense_15/kernel:0dense_15/kernel/Assigndense_15/kernel/read:02dense_15/random_uniform:08
Q
dense_15/bias:0dense_15/bias/Assigndense_15/bias/read:02dense_15/Const:08
`
dense_16/kernel:0dense_16/kernel/Assigndense_16/kernel/read:02dense_16/random_uniform:08
Q
dense_16/bias:0dense_16/bias/Assigndense_16/bias/read:02dense_16/Const:08
`
dense_17/kernel:0dense_17/kernel/Assigndense_17/kernel/read:02dense_17/random_uniform:08
Q
dense_17/bias:0dense_17/bias/Assigndense_17/bias/read:02dense_17/Const:08
`
dense_18/kernel:0dense_18/kernel/Assigndense_18/kernel/read:02dense_18/random_uniform:08
Q
dense_18/bias:0dense_18/bias/Assigndense_18/bias/read:02dense_18/Const:08*Z
serving_defaultG
'
input
input:0���������
output
output:0