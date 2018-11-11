extends KinematicBody2D

const ORIG_POS = Vector2(576,-282)
const TARGET_POS = Vector2(576, 282)
const MOVE_SPEED = 3

var move_up = false

func _ready():
	hide()

func _physics_process(delta):
	if !Network.victorious and !get_tree().current_scene.name == 'Menu':
		return
	if !visible:
		show()
		
	if !move_up:	
		if global_position.distance_to(TARGET_POS) > 1:
			move_and_slide((TARGET_POS - global_position) * MOVE_SPEED)
	else:
		if global_position.distance_to(ORIG_POS) > 1:
			move_and_slide((ORIG_POS - global_position) * MOVE_SPEED)
		else:
			get_tree().change_scene('res://interface/Menu.tscn')


func _on_PanelButton_pressed():
	move_up = true
