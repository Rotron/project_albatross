extends StaticBody2D

var opening = false
var item_res = null

func _ready():
	$AnimationPlayer.play('idle')
	var item = get_item()
	item_res = load("res://scriptable_objects/powerups/" + item + ".tscn")
	
func open(player):
	if check_net_is_claimed() or opening:
		return
	opening = true
	$AnimationPlayer.play('open')
	var item_spawn = item_res.instance()
	add_child(item_spawn)
	item_spawn.spawned = true
	if !$'/root/Game'.force_local and player.is_master:
		Network.google_send_reliable( { chest_num = get_chest_number() } )
	
func check_net_is_claimed():
	return Network.chests[get_chest_number()-1]
	
func get_chest_number():
	var chest_name = self.name
	var length = len(chest_name)
	return int(chest_name.substr(length-1, length))
	
func get_item():
	#only one item now use random later
	return 'Health'